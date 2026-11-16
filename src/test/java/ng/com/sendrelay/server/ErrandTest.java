package ng.com.sendrelay.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class ErrandTest {
    private static final Tasks.Location SHOP = new Tasks.Location(6.4433, 3.4796, "Shoprite, Lekki");

    @Test
    void buildersWriteErrandFieldsAtTheStageTopLevel() {
        Tasks.Stage purchase = Errand.purchase(
                SHOP,
                "",
                List.of(new Errand.PurchaseItem("Peak milk 400g", 2, null, 350_000L, null, "ASK")),
                Errand.work().withReference("groceries").withPayWith("PREPAID"));
        JsonNode json = Relay.JSON.valueToTree(purchase);
        assertEquals("PURCHASE", json.get("type").asText());
        assertEquals("groceries", json.get("reference").asText());
        assertEquals("PREPAID", json.get("payWith").asText());
        assertEquals("Peak milk 400g", json.at("/items/0/name").asText());
        assertEquals(350_000L, json.at("/items/0/maxUnitPrice").asLong());
        assertFalse(json.at("/items/0").has("estimatedValue"), "a shopping-list line carries no parcel fields");
        assertFalse(json.has("work"));

        JsonNode payment = Relay.JSON.valueToTree(Errand.payment(SHOP, "", "Eko Electricity", 1_500_000L, Errand.work().withBillReference("0123456789")));
        assertEquals("Eko Electricity", payment.get("payee").asText());
        assertEquals(1_500_000L, payment.get("amount").asLong());
        JsonNode custom = Relay.JSON.valueToTree(Errand.custom(SHOP, "Hand the keys to the gateman", "S", null));
        assertEquals("S", custom.get("effort").asText());
    }

    @Test
    void stagesReadBackTheSameJson() throws Exception {
        String raw = "{\"type\":\"PURCHASE\",\"location\":{\"latitude\":1.0,\"longitude\":2.0,\"address\":\"a\"},"
                + "\"items\":[{\"name\":\"Milk\",\"quantity\":2,\"ifUnavailable\":\"ASK\"}],\"spendLimit\":500000,"
                + "\"approval\":{\"id\":\"apr_1\",\"stageIndex\":0,\"kind\":\"SUBSTITUTE\",\"status\":\"PENDING\"}}";
        Tasks.Stage stage = Relay.JSON.readValue(raw, Tasks.Stage.class);
        assertNull(stage.items());
        assertEquals("ASK", stage.work().purchaseItems().get(0).ifUnavailable());
        assertEquals("PENDING", stage.work().approval().status());
        assertEquals(Relay.JSON.readTree(raw), Relay.JSON.readTree(Relay.JSON.writeValueAsString(stage)));

        String pickup = "{\"type\":\"PICKUP\",\"location\":{\"latitude\":1.0,\"longitude\":2.0,\"address\":\"a\"},"
                + "\"items\":[{\"name\":\"Parcel\",\"estimatedValue\":500000,\"estimatedWeight\":\"LIGHT\",\"estimatedSize\":\"BOX\"}]}";
        Tasks.Stage plain = Relay.JSON.readValue(pickup, Tasks.Stage.class);
        assertNull(plain.work(), "a stage without errand fields has no work");
        assertEquals(500_000L, plain.items().get(0).estimatedValue());
        assertEquals(Relay.JSON.readTree(pickup), Relay.JSON.readTree(Relay.JSON.writeValueAsString(plain)));
    }
}
