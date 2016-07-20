/*
 * LICENCE : CloudUnit is available under the Affero Gnu Public License GPL V3 : https://www.gnu.org/licenses/agpl-3.0.html
 *     but CloudUnit is licensed too under a standard commercial license.
 *     Please contact our sales team if you would like to discuss the specifics of our Enterprise license.
 *     If you are not sure whether the GPL is right for you,
 *     you can always test our software under the GPL and inspect the source code before you contact us
 *     about purchasing a commercial license.
 *
 *     LEGAL TERMS : "CloudUnit" is a registered trademark of Treeptik and can't be used to endorse
 *     or promote products derived from this project without prior written permission from Treeptik.
 *     Products or services derived from this software may not be called "CloudUnit"
 *     nor may "Treeptik" or similar confusing terms appear in their names without prior written permission.
 *     For any questions, contact us : contact@treeptik.fr
 */

package fr.treeptik.cloudunit.servers;

import fr.treeptik.cloudunit.exception.ServiceException;
import fr.treeptik.cloudunit.initializer.CloudUnitApplicationContext;
import fr.treeptik.cloudunit.model.User;
import fr.treeptik.cloudunit.service.UserService;
import fr.treeptik.cloudunit.utils.TestUtils;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.inject.Inject;
import javax.servlet.Filter;
import java.util.Random;

import static fr.treeptik.cloudunit.utils.TestUtils.getUrlContentPage;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by nicolas on 08/09/15.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration(classes = {CloudUnitApplicationContext.class, MockServletContext.class})
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@ActiveProfiles("integration")
public abstract class AbstractApplicationControllerTestIT {

    protected String release;

    private final Logger logger = LoggerFactory.getLogger(AbstractApplicationControllerTestIT.class);

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Inject
    private AuthenticationManager authenticationManager;

    @Autowired
    private Filter springSecurityFilterChain;

    @Inject
    private UserService userService;

    private MockHttpSession session;

    private static String applicationName;

    @BeforeClass
    public static void initEnv() {
        applicationName = "app" + new Random().nextInt(100000);
    }

    @Before
    public void setup() {
        logger.info("setup");

        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();

        User user = null;
        try {
            user = userService.findByLogin("johndoe");
        } catch (ServiceException e) {
            logger.error(e.getLocalizedMessage());
        }

        Authentication authentication = null;
        if (user != null) {
            authentication = new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword());
        }
        Authentication result = authenticationManager.authenticate(authentication);
        SecurityContext securityContext = SecurityContextHolder.getContext();
        securityContext.setAuthentication(result);
        session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
    }

    @After
    public void teardown() throws Exception {
        logger.info("teardown");

        ResultActions resultats =
                mockMvc.perform(delete("/application/" + applicationName).session(session).contentType(MediaType.APPLICATION_JSON));
        resultats.andExpect(status().isOk());

        SecurityContextHolder.clearContext();
        session.invalidate();
    }

    @Before
    public void createApplication() {
        try {
            final String jsonString =
                    "{\"applicationName\":\"" + applicationName + "\", \"serverName\":\"" + release + "\"}";
            ResultActions resultats =
                    mockMvc.perform(post("/application").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString));
            resultats.andExpect(status().isOk());

            resultats =
                    mockMvc.perform(get("/application/" + applicationName).session(session).contentType(MediaType.APPLICATION_JSON));
            resultats.andExpect(jsonPath("name").value(applicationName.toLowerCase()));
        } catch(Exception e) {
            logger.error(e.getLocalizedMessage());
        }
    }
    /**
     * We cannot create an application with an empty name.
     *
     * @throws Exception
     */
    @Test
    public void test011_FailCreateEmptyNameApplication()
        throws Exception {
        logger.info("Create application with an empty name");
        final String jsonString = "{\"applicationName\":\"" + "" + "\", \"serverName\":\"" + release + "\"}";
        ResultActions resultats =
            this.mockMvc.perform(post("/application")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonString));
        resultats.andExpect(status().is4xxClientError());
    }

    /**
     * We cannot create an application with an wrong syntax name.
     *
     * @throws Exception
     */
    @Test(timeout = 30000)
    public void test012_FailCreateWrongNameApplication()
        throws Exception {
        logger.info("Create application with a wrong syntax name");
        final String jsonString = "{\"applicationName\":\"" + "         " + "\", \"serverName\":\"" + release + "\"}";
        ResultActions resultats =
            this.mockMvc.perform(post("/application").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString));
        resultats.andExpect(status().is4xxClientError());
    }


    @Test(timeout = 30000)
    public void test013_CreateAccentNameApplication()
            throws Exception {

        String accentName = "àéèîôù";
        String deAccentName = "aeeiou";

        logger.info("**************************************");
        logger.info("Create application with accent name " + accentName);
        logger.info("**************************************");

        final String jsonString = "{\"applicationName\":\"" + accentName + "\", \"serverName\":\"" + release + "\"}";
        ResultActions resultats =
                this.mockMvc.perform(post("/application").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString));
        resultats.andExpect(status().isOk());

        logger.info("**************************************");
        logger.info("Delete application : " + deAccentName);
        logger.info("**************************************");
        resultats =
                mockMvc.perform(delete("/application/" + deAccentName).session(session).contentType(MediaType.APPLICATION_JSON));
        resultats.andExpect(status().isOk());
    }

    @Test()
    public void test031_StartStopStartApplicationTest()
            throws Exception {
        logger.info("Start the application : " + applicationName);
        String jsonString = "{\"applicationName\":\"" + applicationName + "\"}";
        ResultActions resultats = mockMvc.perform(post("/application/start").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString));
        resultats.andExpect(status().isOk());

        logger.info("Stop the application : " + applicationName);
        resultats = mockMvc.perform(post("/application/stop").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString));
        resultats.andExpect(status().isOk());

        logger.info("Start the application : " + applicationName);
        resultats = mockMvc.perform(post("/application/start").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString));
        resultats.andExpect(status().isOk());
    }

    @Test()
    public void test040_ChangeJvmMemorySizeApplicationTest()
        throws Exception {
        logger.info("Change JVM Memory !");
        final String jsonString =
            "{\"applicationName\":\"" + applicationName
                + "\",\"jvmMemory\":\"512\",\"jvmOptions\":\"\",\"jvmRelease\":\"jdk1.8.0_25\",\"location\":\"webui\"}";
        ResultActions resultats =
            this.mockMvc.perform(put("/server/configuration/jvm").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString));
        resultats.andExpect(status().isOk());
    }

    @Test(timeout = 30000)
    public void test041_ChangeInvalidJvmMemorySizeApplicationTest()
        throws Exception {
        logger.info("Change JVM Memory size with an incorrect value : number not allowed");
        String jsonString =
            "{\"applicationName\":\"" + applicationName
                + "\",\"jvmMemory\":\"666\",\"jvmOptions\":\"\",\"jvmRelease\":\"\"}";
        ResultActions resultats =
            mockMvc.perform(put("/server/configuration/jvm").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString));
        resultats.andExpect(status().is4xxClientError());

        logger.info("Change JVM Memory size with an empty value");
        jsonString =
            "{\"applicationName\":\"" + applicationName
                + "\",\"jvmMemory\":\"\",\"jvmOptions\":\"\",\"jvmRelease\":\"jdk1.8.0_25\"}";
        resultats =
            mockMvc.perform(put("/server/configuration/jvm").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString));
        resultats.andExpect(status().is4xxClientError());
    }

    @Test(timeout = 60000)
    public void test050_ChangeJvmOptionsApplicationTest()
        throws Exception {
        logger.info("Change JVM Options !");
        final String jsonString =
            "{\"applicationName\":\"" + applicationName
                + "\",\"jvmMemory\":\"512\",\"jvmOptions\":\"-Dkey1=value1\",\"jvmRelease\":\"jdk1.8.0_25\"}";
        ResultActions resultats =
            mockMvc.perform(put("/server/configuration/jvm").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString));
        resultats.andExpect(status().isOk());

        resultats =
            mockMvc.perform(get("/application/" + applicationName).session(session).contentType(MediaType.APPLICATION_JSON));
        resultats.andExpect(jsonPath("$.servers[0].jvmMemory").value(512)).andExpect(jsonPath(
            "$.servers[0].jvmRelease").value("jdk1.8.0_25")).andExpect(jsonPath(
            "$.servers[0].jvmOptions").value("-Dkey1=value1"));
    }

    @Test(timeout = 30000)
    public void test051_ChangeFailWithXmsJvmOptionsApplicationTest()
        throws Exception {
        logger.info("Change JVM With Xms : not allowed");
        final String jsonString =
            "{\"applicationName\":\"" + applicationName
                + "\",\"jvmMemory\":\"512\",\"jvmOptions\":\"-Xms=512m\",\"jvmRelease\":\"jdk1.8.0_25\"}";
        ResultActions resultats =
            mockMvc.perform(put("/server/configuration/jvm").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString));
        resultats.andExpect(status().is4xxClientError());
    }

    @Test()
    public void test050_OpenAPort()
            throws Exception {
        logger.info("Open custom ports !");
        String jsonString =
                "{\"applicationName\":\"" + applicationName
                        + "\",\"portToOpen\":\"6115\",\"alias\":\"access6115\"}";
        ResultActions resultats =
                this.mockMvc.perform(post("/server/ports/open").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString));
        resultats.andExpect(status().isOk());

        logger.info("Close custom ports !");
        jsonString =
                "{\"applicationName\":\"" + applicationName
                        + "\",\"portToOpen\":\"6115\",\"alias\":\"access6115\"}";
        resultats =
                this.mockMvc.perform(post("/server/ports/close").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString));
        resultats.andExpect(status().isOk());
    }

   /* @Test()
    public void test060_testJolokiaLibTest() throws Exception {
        logger.info("Testing jolokia war the application : " + applicationName);
        String urlToCall = "http://" + applicationName.toLowerCase() + "-johndoe-admin.cloudunit.dev/jolokia";
        String contentPage = getUrlContentPage(urlToCall);
        int counter = 0;
        while ((contentPage.isEmpty() ||contentPage.contains("Error 502 - Application Not Responding") || (contentPage.contains("404"))  && counter++ < TestUtils.NB_ITERATION_MAX)) {
            contentPage = getUrlContentPage(urlToCall);
            Thread.sleep(1000);
        }
        Assert.assertTrue(contentPage.contains("\"status\":200"));
    }*/
}