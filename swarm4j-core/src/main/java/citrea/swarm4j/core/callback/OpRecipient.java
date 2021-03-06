package citrea.swarm4j.core.callback;

import citrea.swarm4j.core.SwarmException;
import citrea.swarm4j.core.spec.FullSpec;
import com.eclipsesource.json.JsonValue;


import java.util.EventListener;

/**
 * Created with IntelliJ IDEA.
 *
 * @author aleksisha
 *         Date: 29/10/13
 *         Time: 01:49
 */
public interface OpRecipient extends EventListener {

    public static OpRecipient NOOP = new OpRecipient() {
        @Override
        public void deliver(FullSpec spec, JsonValue value, OpRecipient source) throws SwarmException {
            //do nothing
        }

        @Override
        public String toString() {
            return "NOOP";
        }
    };

    void deliver(FullSpec spec, JsonValue value, OpRecipient source) throws SwarmException;
}
