## Designing a URL Shortening service like TinyURL
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