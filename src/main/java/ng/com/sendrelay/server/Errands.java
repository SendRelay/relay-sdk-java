package ng.com.sendrelay.server;

/** The errand price list and limits. Errand stages are built with {@link Errand}. */
public final class Errands {
    private final Relay relay;

    Errands(Relay relay) {
        this.relay = relay;
    }

    /** The stage fees, paid minutes and limits in force for your account. */
    public Errand.Options options() {
        return relay.get("/sdk/errands/options", null, Errand.Options.class);
    }
}
