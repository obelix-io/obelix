import graph.impl.NeoGraphDatabase;
import graph.interfaces.GraphDatabase;
import metrics.MetricsCollector;
import obelix.ObelixBatchImport;
import obelix.ObelixCache;
import obelix.ObelixFeeder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import queue.impl.RedisObelixQueue;
import queue.interfaces.ObelixQueue;
import store.impl.RedisObelixStore;
import web.ObelixWebServer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class Main {

    public static final int MAX_RELATIONSHIPS_DEFAULT = 30;
    public static final int NUMBER_OF_WORKERS_DEFAULT = 1;
    public static final int OBELIX_WEB_PORT_DEFAULT = 4500;
    public static final int NEO4J_WEB_PORT_DEFAULT = 7575;
    public static final int MAX_GRAPH_DEPTH_LIMIT = 10;

    private Main() {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class.getName());

    public static void main(final String... args) {
        LOGGER.warn("Restarting Obelix:main");

        String neoLocation = "graph.db";
        String redisQueuePrefix = "obelix:queue:";
        String redisQueueName = "logentries";

        boolean enableMetrics = false;
        int maxRelationships = MAX_RELATIONSHIPS_DEFAULT;
        int workers = NUMBER_OF_WORKERS_DEFAULT;
        int webPort = OBELIX_WEB_PORT_DEFAULT;
        int configNeo4jWebPort = NEO4J_WEB_PORT_DEFAULT;

        int carg = 0;
        for (String arg : args) {
            if (arg.equals("--neo4jStore")) {
                neoLocation = args[carg + 1];
            }
            carg += 1;
        }

        carg = 0;
        for (String arg : args) {
            if (arg.equals("--max-relationships")) {
                maxRelationships = Integer.parseInt(args[carg + 1]);
            }
            carg += 1;
        }

        carg = 0;
        for (String arg : args) {
            if (arg.equals("--workers")) {
                workers = Integer.parseInt(args[carg + 1]);
            }
            carg += 1;
        }

        carg = 0;
        for (String arg : args) {
            if (arg.equals("--enable-metrics")) {
                enableMetrics = true;
            }
            carg += 1;
        }

        carg = 0;
        for (String arg : args) {
            if (arg.equals("--redis-queue-name")) {
                redisQueueName = args[carg + 1];
            }
            carg += 1;
        }

        carg = 0;
        for (String arg : args) {
            if (arg.equals("--redis-queue-prefix")) {
                redisQueuePrefix = args[carg + 1];
            }
            carg += 1;
        }

        carg = 0;
        for (String arg : args) {
            if (arg.equals("--web-port")) {
                webPort = Integer.parseInt(args[carg + 1]);
            }
            carg += 1;
        }

        boolean batchImportAll = false;
        carg = 0;
        for (String arg : args) {
            if (arg.equals("--batch-import-all")) {
                batchImportAll = true;
            }
            carg += 1;
        }

        boolean enableNeo4jWebServer = false;
        carg = 0;
        for (String arg : args) {
            if (arg.equals("--neo4j-webserver")) {
                enableNeo4jWebServer = true;
                try {
                    configNeo4jWebPort = Integer.parseInt(args[carg + 1]);
                } catch (NumberFormatException | NullPointerException
                        | ArrayIndexOutOfBoundsException e) {
                    LOGGER.info("Neo4jWebServer is started on " + configNeo4jWebPort);
                }
                break;
            }
            carg += 1;
        }

        boolean buildForAllUsersOnStartup = false;
        carg = 0;
        for (String arg : args) {
            if (arg.equals("--build-cache-for-all-users-on-startup")) {
                buildForAllUsersOnStartup = true;
            }
            carg += 1;
        }

        String recommendationDepth = "4";
        carg = 0;
        for (String arg : args) {
            if (arg.equals("--recommendation-depth")) {
                try {
                    int depth = Integer.parseInt(args[carg + 1]);

                    if (depth < 0 || depth > MAX_GRAPH_DEPTH_LIMIT) {
                        throw new NumberFormatException();
                    }

                    recommendationDepth = args[carg + 1];

                } catch (NumberFormatException e) {
                    LOGGER.error("Wrong format for --recommendation-depth"
                            + "option, use a number from 0-10");
                }
            }
            carg += 1;
        }

        LOGGER.info("Starting Obelix");
        LOGGER.info("all args: " + Arrays.toString(args));
        LOGGER.info("--neo4jStore: " + neoLocation);
        LOGGER.info("--max-relationships: " + maxRelationships);
        LOGGER.info("--workers: " + workers);
        LOGGER.info("--redis-queue-name: " + redisQueueName);
        LOGGER.info("--web-port: " + webPort);

        if (batchImportAll) {
            LOGGER.info("Starting batch import of all");
            ObelixBatchImport.run(neoLocation, redisQueueName);
            LOGGER.info("Done importing everything! woho!");
            System.exit(0);
        }

        GraphDatabase graphDb = new NeoGraphDatabase(neoLocation,
                enableNeo4jWebServer, configNeo4jWebPort);

        ObelixQueue redisQueueManager = new RedisObelixQueue(redisQueuePrefix, redisQueueName);
        ObelixQueue usersCacheQueue = new RedisObelixQueue(redisQueuePrefix, "cache:users");


        MetricsCollector metricsCollector = new MetricsCollector(
                enableMetrics, graphDb, redisQueueManager, usersCacheQueue);

        if (enableMetrics) {
            new Thread(metricsCollector).start();
        }

        (new Thread(new ObelixFeeder(graphDb, metricsCollector, maxRelationships,
                redisQueueManager, usersCacheQueue, 1))).start();

        (new Thread(new ObelixWebServer(graphDb, webPort,
                recommendationDepth, clientSettings()))).start();

        (new Thread(new ObelixCache(graphDb, metricsCollector, usersCacheQueue,
                new RedisObelixStore(redisQueuePrefix),
                buildForAllUsersOnStartup, recommendationDepth, maxRelationships
        ))).start();

    }

    public static Map<String, String> clientSettings() {
        Map<String, String> result = new HashMap<>();
        result.put("redis_prefix", "obelix::");
        result.put("recommendations_impact", "0");
        result.put("method_switch_limit", "20");
        result.put("score_lower_limit", "0.20");
        result.put("score_upper_limit", "1");
        result.put("score_min_limit", "10");
        result.put("score_min_multiply", "4");
        result.put("score_one_result", "1.0");
        result.put("redis_timeout_recommendations_cache", "30");
        result.put("redis_timeout_search_result", "3000");
        return result;
    }
}
