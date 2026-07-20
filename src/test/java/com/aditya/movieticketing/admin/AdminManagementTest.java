package com.aditya.movieticketing.admin;

import com.aditya.movieticketing.AbstractIntegrationTest;
import com.aditya.movieticketing.repository.MovieRepository;
import com.aditya.movieticketing.repository.TheaterRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin management of pricing tiers, refund policies, and seat layouts (screens + seats), including
 * that a freshly created screen/seat layout feeds through to a bookable show. RBAC is verified too:
 * a customer cannot reach these admin endpoints.
 */
class AdminManagementTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TheaterRepository theaterRepository;
    @Autowired private MovieRepository movieRepository;

    @Test
    @DisplayName("admin can create and list a pricing tier; a customer cannot")
    void pricingTiers() throws Exception {
        mockMvc.perform(post("/admin/pricing-tiers").with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"MATINEE\",\"multiplier\":0.80}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("MATINEE"));

        mockMvc.perform(get("/admin/pricing-tiers").with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk());

        // RBAC: a customer is forbidden
        mockMvc.perform(post("/admin/pricing-tiers").with(httpBasic("alice", "alice123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"NOPE\",\"multiplier\":1.0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin can create a refund policy with rules")
    void refundPolicies() throws Exception {
        mockMvc.perform(post("/admin/refund-policies").with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Flexible","active":true,
                                 "rules":[{"minHoursBeforeShow":12,"refundPercentage":75}]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Flexible"))
                .andExpect(jsonPath("$.rules[0].refundPercentage").value(75));
    }

    @Test
    @DisplayName("admin creates a screen + seat layout, then a show on it, which becomes bookable")
    void seatLayoutFeedsBookableShow() throws Exception {
        Long theaterId = theaterRepository.findAll().get(0).getId();
        Long movieId = movieRepository.findAll().get(0).getId();

        // create a screen
        String screenJson = mockMvc.perform(post("/admin/screens").with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theaterId\":" + theaterId + ",\"name\":\"Layout Test Screen\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long screenId = objectMapper.readTree(screenJson).get("id").asLong();

        // add a 5-seat row
        mockMvc.perform(post("/admin/screens/{id}/seats", screenId).with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[{\"rowLabel\":\"Z\",\"count\":5,\"seatClassName\":\"REGULAR\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(5));

        // create a pricing tier to price the show
        String tierJson = mockMvc.perform(post("/admin/pricing-tiers").with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"LAYOUT_TIER\",\"multiplier\":1.0}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long tierId = objectMapper.readTree(tierJson).get("id").asLong();

        // create a show on the new screen
        String start = Instant.now().plus(2, ChronoUnit.DAYS).toString();
        String end = Instant.now().plus(2, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS).toString();
        String showJson = mockMvc.perform(post("/admin/shows").with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movieId\":" + movieId + ",\"screenId\":" + screenId
                                + ",\"pricingTierId\":" + tierId + ",\"startsAt\":\"" + start
                                + "\",\"endsAt\":\"" + end + "\",\"basePrice\":200}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long showId = objectMapper.readTree(showJson).get("showId").asLong();

        // the show is bookable: its seat map has the 5 seats we laid out
        mockMvc.perform(get("/shows/{id}/seats", showId).with(httpBasic("alice", "alice123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }
}
