package citrea.swarm4j.core.model;

import citrea.swarm4j.core.SwarmException;
import citrea.swarm4j.core.callback.OpRecipient;
import citrea.swarm4j.core.callback.Uplink;
import citrea.swarm4j.core.pipe.LoopbackOpChannelFactory;
import citrea.swarm4j.core.pipe.Pipe;
import citrea.swarm4j.core.spec.*;

import citrea.swarm4j.core.storage.InMemoryStorage;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import org.junit.*;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Created with IntelliJ IDEA.
 *
 * @author aleksisha
 *         Date: 28.08.2014
 *         Time: 22:48
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class OnOffTest {

    private static final Logger logger = LoggerFactory.getLogger(OnOffTest.class);
    private static final IdToken SERVER = new IdToken("#" + Host.SERVER_HOST_ID_PREFIX + "up");
    private static final IdToken CLIENT = new IdToken("#client");
    private static final IdToken DUMMY_STORAGE_ID = new IdToken("#dummy");
    private static final int RECONNECT_TIMEOUT = 10;

    //TODO cache-storage private Thread cacheStorageThread;

    //TODO cache-storage private XInMemoryStorage cacheStorage;

    private Host server;
    private Host client;

    @Before
    public void setUp() throws Exception {

        InMemoryStorage dummyStorage;
        dummyStorage = new InMemoryStorage(DUMMY_STORAGE_ID);
        dummyStorage.setAsync(true);

        server = new Host(SERVER, dummyStorage);
        server.setAsync(true);
        server.registerType(Duck.class);
        server.registerType(Thermometer.class);
        server.start();
        server.waitForStart();

        //cacheStorage = new XInMemoryStorage(new SpecToken("#cache"));
        //cacheStorageThread = new Thread(cacheStorage);
        //cacheStorageThread.start();

        client = new Host(CLIENT);
        client.setAsync(true);
        client.registerType(Duck.class);
        client.registerType(Thermometer.class);
        client.registerChannelFactory(LoopbackOpChannelFactory.SCHEME, new LoopbackOpChannelFactory(server));
        client.start();
        client.waitForStart();

        client.connect(new URI(LoopbackOpChannelFactory.SCHEME + "://server"), RECONNECT_TIMEOUT, 0);
    }

    @After
    public void tearDown() throws Exception {
        client.close();
        client.stop();
        client = null;

        server.close();
        server.stop();
        server = null;
    }

    @Test
    public void test3a_serialized_on_reon() throws SwarmException, InterruptedException {
        logger.info("3.a serialized on, reon");
        // that's the default server.getSources = function () {return [storage]};
        final TypeIdSpec THERM_ID = new TypeIdSpec("/Thermometer#room");

        client.on(JsonValue.valueOf(THERM_ID.toString() + Syncable.INIT.toString()), new OpRecipient() {
            @Override
            public void deliver(FullSpec spec, JsonValue value, OpRecipient source) throws SwarmException {
                Thermometer obj = (Thermometer) client.objects.get(THERM_ID);
                JsonObject fieldValues = new JsonObject();
                fieldValues.set("t", 22);
                obj.set(fieldValues);
            }

            @Override
            public String toString() {
                return "init-listener";
            }
        });
        Thread.sleep(100);

        Thermometer o = (Thermometer) server.objects.get(THERM_ID);
        assertNotNull(o);
        assertEquals(22, o.t);
    }

    @Test
    public void test3b_pipe_reconnect_backoff() throws SwarmException, InterruptedException {
        logger.info("3.b pipe reconnect, backoff");
        Thermometer thermometer = server.get(Thermometer.class);
        final AtomicInteger counter = new AtomicInteger(0);

        // OK. The idea is to connect/disconnect it 100 times then
        // check that the state is OK, there are no zombie listeners
        // no objects/hosts, log is 1 record long (distilled) etc

        client.on(JsonValue.valueOf(thermometer.getTypeId().toString() + Model.SET.toString()), new OpRecipient() {
            @Override
            public void deliver(FullSpec spec, JsonValue value, OpRecipient source) throws SwarmException {
                logger.debug("{} <= ({}, {}, {})", this, spec, value, source);
                if (Model.SET.equals(spec.getOp())) {
                    for (Uplink connection : client.getSources(client.getTypeId())) {
                        if (connection instanceof Pipe) {
                            ((Pipe) connection).getChannel().close();
                        }
                    }
                    counter.incrementAndGet();
                }
            }

            @Override
            public String toString() {
                return "ThermometerListener";
            }
        });

        for (int i = 0; i < 10; i++) {
            Thread.sleep(100);
            JsonObject fieldValues = new JsonObject();
            fieldValues.set("t", i);
            thermometer.set(fieldValues);
        }

        Thread.sleep(500);
        assertEquals("reconnected 10 times", 10, counter.get());
    }

    // TODO disconnection events
    @Test
    @Ignore
    public void test3c_disconnection_events() throws SwarmException, InterruptedException {
        logger.info("3.c Disconnection events");

        final AtomicInteger counter = new AtomicInteger(0);

        Thread.sleep(100);

        client.on(Syncable.REOFF.toJson(), new OpRecipient() {
            @Override
            public void deliver(FullSpec spec, JsonValue value, OpRecipient source) throws SwarmException {
                assertSame(source, client);
                assertTrue(source instanceof Host);
                assertFalse(!((Host) source).isNotUplinked());
                counter.incrementAndGet();
            }

            @Override
            public String toString() {
                return "reoff-listener";
            }
        });

        client.on(Syncable.REON.toJson(), new OpRecipient() {
            @Override
            public void deliver(FullSpec spec, JsonValue value, OpRecipient source) throws SwarmException {
                assertEquals(spec.getId(), client.getId());
            }

            @Override
            public String toString() {
                return "reon-listener";
            }
        });

        Thread.sleep(100);
        client.disconnect(server.getPeerId());

        Thread.sleep(100);
        assertEquals(3, counter.get());
    }
}