package com.hub.gisdatahub.seoul.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

import com.hub.gisdatahub.seoul.config.SeoulOpenApiProperties;

class SeoulOpenApiClientTest {

    private MockRestServiceServer server;
    private SeoulOpenApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        SeoulOpenApiProperties properties = new SeoulOpenApiProperties();
        properties.setBaseUrl("http://openapi.seoul.go.kr:8088");
        properties.setKey("test-key");

        client = new SeoulOpenApiClient(restClient, properties);
    }

    @AfterEach
    void verify() {
        server.verify();
    }

    @Test
    void callSeoulOpenApi_buildsExpectedUrl() {
        server.expect(MockRestRequestMatchers.requestTo(
                        "http://openapi.seoul.go.kr:8088/test-key/json/RealtimeCityAir/1/25/"))
                .andRespond(MockRestResponseCreators.withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        String body = client.callSeoulOpenApi("RealtimeCityAir", 1, 25);

        assertThat(body).contains("ok");
    }

    @Test
    void callSeoulOpenApi_appendsPathParams() {
        server.expect(MockRestRequestMatchers.requestTo(
                        "http://openapi.seoul.go.kr:8088/test-key/json/SPOP_LOCAL_RESD_PPLTN/1/5/20240601/"))
                .andRespond(MockRestResponseCreators.withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        String body = client.callSeoulOpenApi("SPOP_LOCAL_RESD_PPLTN", 1, 5, "20240601");

        assertThat(body).contains("ok");
    }

}
