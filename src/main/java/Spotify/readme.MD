## Music Recommendation System

#### SNAKE
- S (Scenario)
    1. output a song/a list of songs for a user
    1. like/dislike
    1. jump to the next song
- N (Necessary)
    - 5 million active users
    - 2 minutes for a song
    - (5 * 10^6) / (2*60) -> around 50000 TPS
        - we may be able to return 100 songs each time -> less TPS
    - check budget requirements for number of servers
    - 2 servers should be sufficient
- A (Application)
    [!pic](./architecture.png)
    - users -> 1 client serving module to serve all requests (persistent connection)
    - client serving module -> messenger module (persistent connection)
    - messenger module -> recommendation calculator
    - Request Use Cases:
        1. request for a song: (user -> waiter -> messenger -> Interest DB)
        1. update interest asynchronously (dislike or like action):
        (user -> waiter -> messenger -> recommendation calculator -> Interest DB)
    - if too many queue messages, can drop some (user interest is not that important)
- K (Data): offline mode to do grouping on users
    - cold start:
        1. Find music that almost everyone loves (do data mining etc.)
        1. maybe: songs that 80% of users have liked (e.g. Hotel California)
    - real-time feed:
        1. the most important feeds are the first 3 times
        1. for the first 3 times (like, dislike), add more weight
    - Diversity:
        - 80% new songs and 20% old songs
        - constantly add new features (a random coefficient)
- Evolve:
    - promote songs that everyone likes
    - create personal music list