## Design Twitter
​
#### Define the Problem:
- what is the mvp of twitter?
  1. User management module
  2. User following each other
  3. Users view each others' feeds
  4. hashtag
  5. activity tracking (optional)
​
#### High-Level Solution
- Data modeling
- How to serve feeds?
  - if someone follows lots of people, how to improve performance?
  - feed ordering abstraction
  - how to implement @ and retweet
  - how to detect fake users?
- how to show trending posts and rank them?
- recommendation system on whom to follow
- search bar feature
​
---
__notes from:__ http://highscalability.com/blog/2013/7/8/the-architecture-twitter-uses-to-deal-with-150m-active-users.html
​
#### Reference Data
- 150M world wide active users
- 300K QPS to generate timelines
- 400M tweets a day
​
#### Key Points from Twitter
- web app -> set of APIs to power web/mobile clients, (Basically one of the largest real-time event buses)
- Twitter: consumption mechanism rather than production mechanism, 300K QPS spent on reading timelines & 6000 requests per second on writes
- outliers, people with huge follower list (fanout problem -> slow), the worst case is when celebrities tweet each other
- Home timeline sits in a Redis cluster with a maximum of 800 entries
- user activity monitoring to analyze user profile
- Twitter's goal is to send a message to a user in no more than 5 seconds (difficult with fanout)
​
#### Key Features
1. Two timelines:
  - user timeline with all tweets a particular user has sent
  - home timeline as a temporal merge of all the user timelines of the people one has followed
  - retweet & @ feature
2. Pull-based vs Push-based:
  - pull:
    - targeted timeline: twitter.com & home_timeline API, REST API to request
    - query timeline: search API to look for tweets matching a particular query
  - push:
    - real-time event systems pushing tweets at 22 MB/sec via Firehose
      - Open a socket to Twitter and they will push all public tweets to you within 150 msec
      - At any given time there's about 1 million sockets open to the push cluster
    - User stream connection. Powers TweetDeck and Twitter for Mac also goes through here. When you login they look at your social graph and only send messages out from people you follow, recreating the home timeline experience. Instead of polling you get the same timeline experience over a persistent connection.
    - Query API. Issue a standing query against tweets. As tweets are created and found matching the the query they are routed out the registered sockets for the query.
​
#### High Level for Pull Based Timelines
- Tweet comes in over a write API. It goes through load balancers and a TFE (Twitter Front End) and other stuff that won't be addressed.
- This is a very directed path. Completely precomputed home timeline. All the business rules get executed as tweets come in.
- Immediately the fanout process occurs. Tweets that come in are placed into a massive Redis cluster. Each tweet is replicated 3 times on 3 different machines. At Twitter scale many machines fail a day.
- Fanout queries the social graph service that is based on Flock. Flock maintains the follower and followings lists.
  - Flock returns the social graph for a recipient and starts iterating through all the timelines stored in the Redis cluster.
  - The Redis cluster has a couple of terabytes of RAM.
  - Pipelined 4k destinations at a time
  - Native list structure are used inside Redis.
  - Let's say you tweet and you have 20K followers. What the fanout daemon will do is look up the location of all 20K users inside the Redis cluster. Then it will start inserting the Tweet ID of the tweet into all those lists throughout the Redis cluster. So for every write of a tweet as many as 20K inserts are occurring across the Redis cluster.
  - What is being stored is the tweet ID of the generated tweet, the user ID of the originator of the tweet, and 4 bytes of bits used to mark if it's a retweet or a reply or something else.
  - Your home timeline sits in a Redis cluster and is 800 entries long. If you page back long enough you'll hit the limit. RAM is the limiting resource determining how long your current tweet set can be.
  - Every active user is stored in RAM to keep latencies down.
  - Active user is someone who has logged into Twitter within 30 days, which can change depending on cache capacity or Twitter's usage.
  - If you are not an active user then the tweet does not go into the cache.
  - Only your home timelines hit disk.
  - If you fall out of the Redis cluster then you go through a process called reconstruction.
    - Query against the social graph service. Figure out who you follow. Hit disk for every single one of them and then shove them back into Redis.
    - It's MySQL handling disk storage via Gizzard, which abstracts away SQL transactions and provides global replication.
  - By replicating 3 times if a machine has a problem then they won't have to recreate the timelines for all the timelines on that machine per datacenter.
  - If a tweet is actually a retweet then a pointer is stored to the original tweet.
- When you query for your home timeline the Timeline Service is queried. The Timeline Service then only has to find one machine that has your home timeline on it.
  - Effectively running 3 different hash rings because your timeline is in 3 different places.
  - They find the first one they can get to fastest and return it as fast as they can.
  - The tradeoff is fanout takes a little longer, but the read process is fast. About 2 seconds from a cold cache to the browser. For an API call it's about 400 msec.
- Since the timeline only contains tweet IDs they must "hydrate" those tweets, that is find the text of the tweets. Given an array of IDs they can do a multiget and get the tweets in parallel from T-bird.
- Gizmoduck is the user service and Tweetypie is the tweet object service. Each service has their own caches. The user cache is a memcache cluster that has the entire user base in cache. Tweetypie has about the last month and half of tweets stored in its memcache cluster. These are exposed to internal customers.
- Some read time filtering happens at the edge. For example, filtering out Nazi content in France, so there's read time stripping of the content before it is sent out.
​
#### High Level for Search
- Opposite of pull. All computed on the read path which makes the write path simple.
As a tweet comes in, the Ingester tokenizes and figures out everything they want to index against and stuffs it into a single Early Bird machine. Early Bird is a modified version of Lucene. The index is stored in RAM.
- In fanout a tweet may be stored in N home timelines of how many people are following you, in Early Bird a tweet is only stored in one Early Bird machine (except for replication).
- Blender creates the search timeline. It has to scatter-gather across the datacenter. It queries every Early Bird shard and asks do you have content that matches this query? If you ask for "New York Times" all shards are queried, the results are returned, sorted, merged, and reranked. Rerank is by social proof, which means looking at the number of retweets, favorites, and replies.
- The activity information is computed on a write basis, there's an activity timeline. As you are favoriting and replying to tweets an activity timeline is maintained, similar to the home timeline, it is a series of IDs of pieces of activity, so there's favorite ID, a reply ID, etc.
- All this is fed into the Blender. On the read path it recomputes, merges, and sorts. Returning what you see as the search timeline.
- Discovery is a customized search based on what they know about you. And they know a lot because you follow a lot of people, click on links, that information is used in the discovery search. It reranks based on the information it has gleaned about you.
​
#### Search vs Pull (inverses)
- Search and pull look remarkably similar but they have a property that is inverted from each other.
- On the home timeline:
    - Write. when a tweet  comes in there's an O(n) process to write to Redis clusters, where n is the number of people following you. Painful for Lady Gaga and Barack Obama where they are doing 10s of millions of inserts across the cluster. All the Redis clusters are backing disk, the Flock cluster stores the user timeline to disk, but usually timelines are found in RAM in the Redis cluster.
    - Read. Via API or the web it's 0(1) to find the right Redis machine. Twitter is optimized to be highly available on the read path on the home timeline. Read path is in the 10s of milliseconds. Twitter is primarily a consumption mechanism, not a production mechanism. 300K requests per second for reading and 6000 RPS for writing.
- On the search timeline:
    - Write. when a tweet comes in and hits the Ingester only one Early Bird machine is hit. Write time path is O(1). A single tweet is ingested in under 5 seconds between the queuing and processing to find the one Early Bird to write it to.
    - Read. When a read comes in it must do an 0(n) read across the cluster. Most people don't use search so they can be efficient on how to store tweets for search. But they pay for it in time. Reading is on the order of 100 msecs. Search never hits disk. The entire Lucene index is in RAM so scatter-gather reading is efficient as they never hit disk.
- Text of the tweet is almost irrelevant to most of the infrastructure. T-bird stores the entire corpus of tweets. Most of the text of a tweet is in RAM. If not then hit T-bird and do a select query to get them back out again. Text is almost irrelevant except perhaps on Search, Trends, or What's Happening pipelines. The home timeline doesn't care almost at all.
​
#### Future Considerations
- Fanout is slow for celebrities: stop fanning out for high value users
- balance write and read for high value users can save significant computational resources
​
#### Monitoring
- Dashboards around the office show how the system is performing at any given time.
- If you have 1 million followers it takes a couple of seconds to fanout all the tweets.
- Tweet input statistics: 400m tweets per day; 5K/sec daily average; 7K/sec daily peak; >12K/sec during large events.
- Timeline delivery statistics: 30b deliveries / day (~21m / min); 3.5 seconds @ p50 (50th percentile) to deliver to 1m; 300k deliveries /sec; @ p99 it could take up to 5 minutes
- A system called VIZ monitors every cluster. Median request time to the Timeline Service to get data out of Scala cluster is 5msec. @ p99 it's 100msec. And @ p99.9 is where they hit disk, so it takes a couple hundred of milliseconds.
- Zipkin is based on Google's Dapper system. With it they can taint a request and see every single service it  hits, with request times, so they can get a very detailed idea of performance for each request. You can then drill down and see every single request and understand all the different timings. A lot of time is spent debugging the system by looking at where time is being spent on requests. They can also present aggregate statistics by phase, to see how long fanout or delivery took, for example. It was a 2 year project to get the get for the activities user timeline down to 2 msec. A lot of time was spent fighting GC pauses, fighting memcache lookups, understanding what the topology of the datacenter looks like, and really setting up the clusters for this type of success.
