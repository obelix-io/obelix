import junit.framework.TestCase;
import queue.impl.InternalObelixQueue;
import queue.impl.RedisObelixQueue;
import queue.interfaces.ObelixQueue;

import static java.lang.Math.random;

public class TestObelixRedisQueue extends TestCase {

    public void testRedisQueuePushAndPop() {

        ObelixQueue obelixQueue = new RedisObelixQueue("logentries" + random()*10000);

        obelixQueue.push("1");
        obelixQueue.push("2");
        obelixQueue.push("3");

        assertEquals(obelixQueue.getAll().size(), 3);

        assertEquals(obelixQueue.pop(), "1");
        assertEquals(obelixQueue.pop(), "2");
        assertEquals(obelixQueue.pop(), "3");

    }

    public void testRedisQueueBehaveAsTheInternalQueuePopAndPush() {

        ObelixQueue obelixQueue = new InternalObelixQueue();
        ObelixQueue redisObelixQueue = new RedisObelixQueue("randomQueueName" + random() * 10000);

        for (int i = 0; i < 10000; i++) {
            obelixQueue.push("element" + i);
            redisObelixQueue.push("element" + i);
        }

        for (int i = 0; i < 10000; i++) {
            assertEquals(obelixQueue.pop(), redisObelixQueue.pop());
        }

    }

    public void testRedisQueueBehaveAsTheInternalQueueGetAll() {

        ObelixQueue obelixQueue = new InternalObelixQueue();
        ObelixQueue redisObelixQueue = new RedisObelixQueue("randomQueueName" + random() * 10000);

        for (int i = 0; i < 10000; i++) {
            obelixQueue.push("element" + i);
            redisObelixQueue.push("element" + i);
        }

        assertEquals(obelixQueue.getAll(), redisObelixQueue.getAll());

    }
}