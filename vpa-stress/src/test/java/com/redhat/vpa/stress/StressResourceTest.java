package com.redhat.vpa.stress;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class StressResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
          .when().get("/stress")
          .then()
             .statusCode(200)
             .body(is("Hello RESTEasy"));
    }

}