# Guide for running server

1. Clone [FlameStream repo](https://github.com/flame-stream/FlameStream)
2. In FlameStream root dir: `mvn install -Dmaven.test.skip=true`
3. Clone [FlameNews repo](https://github.com/flame-stream/FlameNews)
4. Install MySQL (I'm using 5.7.22)
5. Run `tbts-schema.sql` on your MySQL instance
6. In FlameNews root dir: `mvn install -Dmaven.test.skip=true`
7. Run `ExpLeagueServer.java`
8. Use [Psi](https://psi-im.org/) and [Psi-plus](https://psi-plus.com/) for chatting :)