package Twitter;

import java.util.*;

/*
355. Design Twitter

Design a simplified version of Twitter where users can post tweets,
follow/unfollow another user and is able to see the 10 most recent tweets in the user's news feed.
Your design should support the following methods:

postTweet(userId, tweetId): Compose a new tweet.
getNewsFeed(userId): Retrieve the 10 most recent tweet ids in the user's news feed.
Each item in the news feed must be posted by users who the user followed or by the user herself.
Tweets must be ordered from most recent to least recent.
follow(followerId, followeeId): Follower follows a followee.
unfollow(followerId, followeeId): Follower unfollows a followee.
Example:

Twitter twitter = new Twitter();

// User 1 posts a new tweet (id = 5).
twitter.postTweet(1, 5);

// User 1's news feed should return a list with 1 tweet id -> [5].
twitter.getNewsFeed(1);

// User 1 follows user 2.
twitter.follow(1, 2);

// User 2 posts a new tweet (id = 6).
twitter.postTweet(2, 6);

// User 1's news feed should return a list with 2 tweet ids -> [6, 5].
// Tweet id 6 should precede tweet id 5 because it is posted after tweet id 5.
twitter.getNewsFeed(1);

// User 1 unfollows user 2.
twitter.unfollow(1, 2);

// User 1's news feed should return a list with 1 tweet id -> [5],
// since user 1 is no longer following user 2.
twitter.getNewsFeed(1);
 */
public class Twitter {

    class Tweet implements Comparable<Tweet> {
        private long timestamp;
        private int val;

        public Tweet(int val) {
            this.val = val;
            this.timestamp = System.currentTimeMillis();
        }

        public int getVal() { return this.val; }

        public long getTimestamp() { return this.timestamp; }

        @Override
        public int compareTo(Tweet o) {
            return -Long.valueOf(this.timestamp).compareTo(o.getTimestamp());
        }
    }

    Map<Integer, List<Tweet>> userTweets;
    Map<Integer, PriorityQueue<Tweet>> userFeeds;
    Map<Integer, Set<Integer>> userRelations;

    /** Initialize your data structure here. */
    public Twitter() {
        userTweets = new HashMap<>();
        userFeeds = new HashMap<>();
        userRelations = new HashMap<>();
    }

    /** Compose a new tweet. */
    public void postTweet(int userId, int tweetId) {
        Tweet tweet = new Tweet(tweetId);
        if (userTweets.containsKey(userId)) {
            userTweets.get(userId).add(tweet);
            userFeeds.get(userId).offer(tweet);
        }
        else {
            userTweets.put(userId, new LinkedList<>(Collections.singleton(tweet)));
            userFeeds.put(userId, new PriorityQueue<>(Collections.singleton(tweet)));
            userRelations.put(userId, new HashSet<>());
        }

        for (int follower:userRelations.get(userId)) userFeeds.get(follower).offer(tweet);
    }

    /** Retrieve the 10 most recent tweet ids in the user's news feed.
     * Each item in the news feed must be posted by users who the user followed or by the user herself.
     * Tweets must be ordered from most recent to least recent. */
    public List<Integer> getNewsFeed(int userId) {
        List<Integer> tweetList = new LinkedList<>();

        if (!userFeeds.containsKey(userId)) return tweetList;

        PriorityQueue<Tweet> tweetPQ = userFeeds.get(userId);
        Iterator<Tweet> tweetIterator = tweetPQ.iterator();

        for (int i=0;i<10 && tweetIterator.hasNext();++i) tweetList.add(tweetIterator.next().getVal());

        return tweetList;
    }

    /** Follower follows a followee. If the operation is invalid, it should be a no-op. */
    public void follow(int followerId, int followeeId) {
        if (!userFeeds.containsKey(followerId)) createUser(followerId);
        if (!userTweets.containsKey(followeeId)) createUser(followeeId);
        userRelations.get(followeeId).add(followerId);
        userFeeds.get(followerId).addAll(userTweets.get(followeeId));
    }

    /** Follower unfollows a followee. If the operation is invalid, it should be a no-op. */
    public void unfollow(int followerId, int followeeId) {
        if (!userFeeds.containsKey(followerId)) createUser(followerId);
        if (!userTweets.containsKey(followeeId)) createUser(followeeId);
        userRelations.get(followeeId).remove(followerId);
        userFeeds.get(followerId).removeAll(userTweets.get(followeeId));
    }

    private void createUser(int userId) {
        userTweets.put(userId, new LinkedList<>());
        userFeeds.put(userId, new PriorityQueue<>());
        userRelations.put(userId, new HashSet<>());
    }

    /**
     * Your Twitter object will be instantiated and called as such:
     * Twitter obj = new Twitter();
     * obj.postTweet(userId,tweetId);
     * List<Integer> param_2 = obj.getNewsFeed(userId);
     * obj.follow(followerId,followeeId);
     * obj.unfollow(followerId,followeeId);
     */

    public static void main(String[] args) {
        System.out.println("Hello World");
    }
}
