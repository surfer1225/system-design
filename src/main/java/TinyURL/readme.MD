## Designing a URL Shortening service like TinyURL
##### Source(https://www.educative.io/collection/page/5668639101419520/5649050225344512/5668600916475904)
- Similar services: bit.ly, goo.gl, qlink.me, etc.

#### 1. Why do we need URL shortening?
- create shorter aliases for long URLs
- short links save a lot of space when displayed, printed, messaged, or tweeted
- e.g:
    - long version: https://www.educative.io/collection/page/5668639101419520/5649050225344512/5668600916475904/
    - short version: http://tinyurl.com/jlg8zpc

#### 2. Requirements & Goals
##### Functional Requirements:
1. Given a URL, our service should generate a shorter and unique alias of it. This is called a short link.
1. When users access a short link, our service should redirect them to the original link.
1. Users should optionally be able to pick a custom short link for their URL.
1. Links will expire after a standard default timespan.
Users should be able to specify the expiration time.

##### Non-Functional Requirements:
1. The system should be highly available. This is required because, if our service is down,
all the URL redirections will start failing.
1. URL redirection should happen in real-time with minimal latency.
1. Shortened links should not be guessable (not predictable).

##### Extended Requirements:
1. Analytics; e.g., how many times a redirection happened?
1. Our service should also be accessible through REST APIs by other services.

#### 3. Capacity Estimation and Constraints
- read heavy
- 100:1 read-to-write

<br></br>
__Traffic estimates__:
1. Assuming, we will have 500M new URL shortenings per month, with 100:1 read/write ratio,
we can expect 50B redirections during the same period:
1. What would be Queries Per Second (QPS) for our system? New URLs shortenings per second:
    - __500 million / (30 days * 24 hours * 3600 seconds) = ~200 URLs/s__
1. Considering 100:1 read/write ratio, URLs redirections per second will be:
    - __100 * 200 URLs/s = 20K/s__

<br></br>
__Strong estimates__:
<br></br>
Let’s assume we store every URL shortening request (and associated shortened link) for 5 years.
Since we expect to have 500M new URLs every month,
the total number of objects we expect to store will be 30 billion:
<br></br>
__500 million * 5 years * 12 months = 30 billion__
<br></br>
<br></br>
Let’s assume that each stored object will be approximately 500 bytes
(just a ballpark estimate–we will dig into it later). We will need 15TB of total storage:
<br></br>
__30 billion * 500 bytes = 15 TB__
<br></br>
<br></br>
__Bandwidth estimates:__
<br></br>
For write requests, since we expect 200 new URLs every second,
total incoming data for our service will be 100KB per second:
<br></br>
__200 * 500 bytes = 100 KB/s__
<br></br>
<br></br>

For read requests, since every second we expect ~20K URLs redirections,
total outgoing data for our service would be 10MB per second:
<br></br>
__20K * 500 bytes = ~10 MB/s__
<br></br>
<br></br>

__Memory estimates:__
<br></br>
If we want to cache some of the hot URLs that are frequently accessed, how much memory will we need to store them? If we follow the 80-20 rule, meaning 20% of URLs generate 80% of traffic, we would like to cache these 20% hot URLs.

Since we have 20K requests per second, we will be getting 1.7 billion requests per day:

__20K * 3600 seconds * 24 hours = ~1.7 billion__
<br></br>
<br></br>
To cache 20% of these requests, we will need 170GB of memory.

__0.2 * 1.7 billion * 500 bytes = ~170GB__
<br></br>
<br></br>
One thing to note here is that since there will be a lot of duplicate requests (of the same URL),
therefore, our actual memory usage will be less than 170GB.

__High level estimates:__
<br></br>
Assuming 500 million new URLs per month and 100:1 read:write ratio,
following is the summary of the high level estimates for our service:

|Metrics|Number|
|---|---|
|New URLs|	200/s|
|URL redirections|20K/s|
|Incoming data|100KB/s|
|Outgoing data|10MB/s|
|Storage for 5 years|15TB|
|Memory for cache|170GB|

#### 4. System APIs
- SOAP or REST APIs to expose functionality of our service
<br></br>
<br></br>
__createURL(api_dev_key, original_url, custom_alias=None, user_name=None, expire_date=None)__
<br></br>
<br></br>
__Parameters:__
1. api_dev_key (string): The API developer key of a registered account.
This will be used to, among other things, throttle users based on their allocated quota.
1. original_url (string): Original URL to be shortened.
1. custom_alias (string): Optional custom key for the URL.
1. user_name (string): Optional user name to be used in encoding.
1. expire_date (string): Optional expiration date for the shortened URL.

__Returns: (string)__
A successful insertion returns the shortened URL; otherwise, it returns an error code.

__deleteURL(api_dev_key, url_key)__
- url_key is a string representing the shortened URL to be retrieved
- A successful deletion returns ‘URL Removed’.

__How do we detect and prevent abuse?__
- A malicious user can put us out of business by consuming all URL keys in the current design.
To prevent abuse, we can limit users via their api_dev_key.
Each api_dev_key can be limited to a certain number of URL creations and redirections per some time period
(which may be set to a different duration per developer key).

#### 5. Database Design
- Defining the DB schema in the early stages of the interview would help to understand the data flow 
among various components and later would guide towards data partitioning.
- A few observations about the nature of the data we will store:
1. We need to store billions of records.
1. Each object we store is small (less than 1K).
1. There are no relationships between records—other than storing which user created a URL.
1. Our service is read-heavy.

__Database Schema:__
- URL: PK(Hash: varchar(16)); OriginalURL: varchar(512); CreationDate: datetime; ExpirationDate: datetime;
UserID: int;
- User: PK(UserID: int); Name: varchar(20); Email: varchar(20); CreationDate: datetime;
LastLogin: datetime;
<br></br>
<br></br>
__What kind of database should we use?__
Since we anticipate storing billions of rows,
and we don’t need to use relationships between objects – 
a NoSQL key-value store like DynamoDB, Cassandra or Riak is a better choice. 
A NoSQL choice would also be easier to scale. 

#### 6. Basic System Design and Algorithm
- The problem we are solving here is, how to generate a short and unique key for a given URL.
  
##### a. Encoding actual URL
- We can compute a unique hash (e.g., MD5 or SHA256, etc.) of the given URL.
The hash can then be encoded for displaying.
This encoding could be base36 ([a-z ,0-9]) or base62 ([A-Z, a-z, 0-9]) and 
if we add ‘-’ and ‘.’ we can use base64 encoding.

__What are different issues with our solution?__
<br></br>
We have the following couple of problems with our encoding scheme:

If multiple users enter the same URL, they can get the same shortened URL, which is not acceptable.
What if parts of the URL are URL-encoded? e.g., http://www.educative.io/distributed.php?id=design,
and http://www.educative.io/distributed.php%3Fid%3Ddesign are identical except for the URL encoding.

__Workaround for the issues:__
<br></br>
We can append an increasing sequence number to each input URL to make it unique,
and then generate a hash of it. We don’t need to store this sequence number in the databases, though.
Possible problems with this approach could be an ever-increasing sequence number.
Can it overflow? Appending an increasing sequence number will also impact the performance of the service.

Another solution could be to append user id (which should be unique) to the input URL.

![pic](./flow.png)

##### b. Generating keys offline
We can have a standalone Key Generation Service (KGS) that generates random six letter strings beforehand
and stores them in a database (let’s call it key-DB).
Whenever we want to shorten a URL, we will just take one of the already-generated keys and use it.
This approach will make things quite simple and fast.
Not only are we not encoding the URL, but we won’t have to worry about duplications or collisions.
KGS will make sure all the keys inserted into key-DB are unique

__Can concurrency cause problems?__
As soon as a key is used, it should be marked in the database to ensure it doesn’t get used again.
If there are multiple servers reading keys concurrently,
we might get a scenario where two or more servers try to read the same key from the database.
How can we solve this concurrency problem?

Servers can use KGS to read/mark keys in the database.
KGS can use two tables to store keys: one for keys that are not used yet,
and one for all the used keys. As soon as KGS gives keys to one of the servers,
it can move them to the used keys table.
KGS can always keep some keys in memory so that it can quickly provide them whenever a server needs them.

For simplicity, as soon as KGS loads some keys in memory,
it can move them to the used keys table. This ensures each server gets unique keys.
If KGS dies before assigning all the loaded keys to some server,
we will be wasting those keys–which is acceptable, given the huge number of keys we have.

KGS also has to make sure not to give the same key to multiple servers.
For that, it must synchronize (or get a lock on) the data structure holding the keys
before removing keys from it and giving them to a server

__What would be the key-DB size?__
With base64 encoding, we can generate 68.7B unique six letters keys.
If we need one byte to store one alpha-numeric character, we can store all these keys in:

__6 (characters per key) * 68.7B (unique keys) = 412 GB.__

__Isn’t KGS a single point of failure?__ Yes, it is. To solve this, we can have a standby replica of KGS.
Whenever the primary server dies, the standby server can take over to generate and provide keys.

__Can each app server cache some keys from key-DB?__ Yes, this can surely speed things up.
Although in this case, if the application server dies before consuming all the keys,
we will end up losing those keys. This can be acceptable since we have 68B unique six letter keys.

__How would we perform a key lookup?__
We can look up the key in our database or key-value store to get the full URL.
If it’s present, issue an “HTTP 302 Redirect” status back to the browser,
passing the stored URL in the “Location” field of the request.
If that key is not present in our system,
issue an “HTTP 404 Not Found” status or redirect the user back to the homepage.

__Should we impose size limits on custom aliases?__
Our service supports custom aliases.
Users can pick any ‘key’ they like, but providing a custom alias is not mandatory.
However, it is reasonable (and often desirable) to impose a size limit on a custom alias to 
ensure we have a consistent URL database.
Let’s assume users can specify a maximum of 16 characters per customer key 
(as reflected in the above database schema).

![pic](./enhanced_flow.png)
<br></br>
<br></br>

##### 7. Data Partitioning and Replication
- To scale out our DB, we need to partition it so that it can store information about billions of URLs.
We need to come up with a partitioning scheme that would divide and store our data to different DB servers.

__a. Range Based Partitioning:__
<br></br>
We can store URLs in separate partitions based on the first letter of the URL or the hash key. Hence we save all the URLs starting with letter ‘A’ in one partition, save those that start with letter ‘B’ in another partition and so on. This approach is called range-based partitioning. We can even combine certain less frequently occurring letters into one database partition. We should come up with a static partitioning scheme so that we can always store/find a file in a predictable manner.

The main problem with this approach is that it can lead to unbalanced servers. e.g. URLs starting with E

__b. Hash-Based Partitioning:__
<br></br>
In this scheme, we take a hash of the object we are storing. We then calculate which partition to use based upon the hash. In our case, we can take the hash of the ‘key’ or the actual URL to determine the partition in which we store the data object.

Our hashing function will randomly distribute URLs into different partitions (e.g., our hashing function can always map any key to a number between [1…256]), and this number would represent the partition in which we store our object.

This approach can still lead to overloaded partitions, which can be solved by using __Consistent Hashing.__

##### 8. Cache
- We can cache URLs that are frequently accessed. We can use some off-the-shelf solution like Memcache, which can store full URLs with their respective hashes.

__How much cache should we have?__
We can start with 20% of daily traffic and, based on clients’ usage pattern,
we can adjust how many cache servers we need.
As estimated above, we need 170GB memory to cache 20% of daily traffic.
Since a modern-day server can have 256GB memory, we can easily fit all the cache into one machine.
Alternatively, we can use a couple of smaller servers to store all these hot URLs.

__Which cache eviction policy would best fit our needs?__
When the cache is full, and we want to replace a link with a newer/hotter URL, how would we choose?
Least Recently Used (LRU) can be a reasonable policy for our system.
Under this policy, we discard the least recently used URL first.

__How can each cache replica be updated?__
Whenever there is a cache miss, our servers would be hitting a backend database.
Whenever this happens, we can update the cache and pass the new entry to all the cache replicas. 

<br></br>
<br></br>
![pic](./with_cache.png)

##### 9. Load Balancer
We can add a Load balancing layer at three places in our system:

1. Between Clients and Application servers
1. Between Application Servers and database servers
1. Between Application Servers and Cache servers

- Initially, we could use a simple Round Robin approach that distributes incoming requests equally among backend servers. This LB is simple to implement and does not introduce any overhead. Another benefit of this approach is that if a server is dead, LB will take it out of the rotation and will stop sending any traffic to it.

- A problem with Round Robin LB is that server load is not taken into consideration. If a server is overloaded or slow, the LB will not stop sending new requests to that server. To handle this, a more intelligent LB solution can be placed that periodically queries the backend server about its load and adjusts traffic based on that.

##### 10. Purging or DB cleanup
Should entries stick around forever or should they be purged? If a user-specified expiration time is reached, what should happen to the link?

If we chose to actively search for expired links to remove them, it would put a lot of pressure on our database. Instead, we can slowly remove expired links and do a lazy cleanup. Our service will make sure that only expired links will be deleted, although some expired links can live longer but will never be returned to users.

- Whenever a user tries to access an expired link, we can delete the link and return an error to the user.
- A separate Cleanup service can run periodically to remove expired links from our storage and cache. This service should be very lightweight and can be scheduled to run only when the user traffic is expected to be low.
- We can have a default expiration time for each link (e.g., two years).
- After removing an expired link, we can put the key back in the key-DB to be reused.
- Should we remove links that haven’t been visited in some length of time, say six months? This could be tricky. Since storage is getting cheap, we can decide to keep links forever.

![pic](./complete.png)

##### 11. Telemetry (Not in MVP)
How many times a short URL has been used, what were user locations, etc.? How would we store these statistics? If it is part of a DB row that gets updated on each view, what will happen when a popular URL is slammed with a large number of concurrent requests?

Some statistics worth tracking: country of the visitor, date and time of access, web page that refers the click, browser, or platform from where the page was accessed.

##### 12. Security and Permissions
Can users create private URLs or allow a particular set of users to access a URL?

We can store permission level (public/private) with each URL in the database. We can also create a separate table to store UserIDs that have permission to see a specific URL. If a user does not have permission and tries to access a URL, we can send an error (HTTP 401) back. Given that we are storing our data in a NoSQL wide-column database like Cassandra, the key for the table storing permissions would be the ‘Hash’ (or the KGS generated ‘key’). The columns will store the UserIDs of those users that have permissions to see the URL.