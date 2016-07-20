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

package fr.treeptik.cloudunit.snapshot;

import static fr.treeptik.cloudunit.utils.TestUtils.downloadAndPrepareFileToDeploy;
import static fr.treeptik.cloudunit.utils.TestUtils.getUrlContentPage;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.treeptik.cloudunit.exception.ServiceException;
import fr.treeptik.cloudunit.initializer.CloudUnitApplicationContext;
import fr.treeptik.cloudunit.model.User;
import fr.treeptik.cloudunit.service.UserService;
import fr.treeptik.cloudunit.utils.TestUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Random;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.servlet.Filter;

/**
 * Created by nicolas on 08/09/15.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration(classes = {CloudUnitApplicationContext.class, MockServletContext.class})
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@ActiveProfiles("integration")
public abstract class AbstractSnapshotControllerTestIT {

    private final Logger logger = LoggerFactory.getLogger(AbstractSnapshotControllerTestIT.class);

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

    private final static String tagName = "mytag";

    protected String release;

    @Value("${cloudunit.instance.name}")
    private String cuInstanceName;

    @BeforeClass
    public static void initEnv() {
        applicationName = "app" + new Random().nextInt(10000);
    }

    @Value("${suffix.cloudunit.io}")
    private String domainSuffix;

    @Value("#{systemEnvironment['CU_SUB_DOMAIN']}")
    private String subdomain;

    private String domain;

    @PostConstruct
    public void init () {
        if (subdomain != null) {
            domain = subdomain + domainSuffix;
        } else {
            domain = domainSuffix;
        }
    }

    @Before
    public void setup() throws Exception {
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

        logger.info("**************************************");
        logger.info("Create Tomcat server");
        logger.info("**************************************");

        String jsonString = "{\"applicationName\":\"" + applicationName + "\", \"serverName\":\"" + release + "\"}";
        ResultActions resultats =
                mockMvc.perform(post("/application").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString));
        resultats.andExpect(status().isOk());
    }

    @After
    public void teardown() throws Exception {
        logger.info("teardown");

        logger.info("**************************************");
        logger.info("Delete application : " + applicationName);
        logger.info("**************************************");
        ResultActions resultats =
                mockMvc.perform(delete("/application/" + applicationName).session(session).contentType(MediaType.APPLICATION_JSON));
        resultats.andExpect(status().isOk());

        SecurityContextHolder.clearContext();
        session.invalidate();
    }

    @Test()
    public void test010_CreateSimpleApplicationSnapshot()
            throws Exception {
        logger.info("**************************************");
        logger.info("Create a snapshot");
        logger.info("**************************************");

        String jsonString =
                "{\"applicationName\":\"" + applicationName + "\", \"tag\":\"" + tagName
                        + "\", \"description\":\"This is a test snapshot\"}";
        logger.info(jsonString);
        ResultActions resultats =
                mockMvc.perform(post("/snapshot").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString)).andDo(print());
        resultats.andExpect(status().isOk());

        logger.info("**************************************");
        logger.info("List the snapshot");
        logger.info("**************************************");

        resultats = mockMvc.perform(get("/snapshot/list").session(session)).andDo(print());
        resultats.andExpect(status().isOk()).andExpect(jsonPath("$[0].tag").value(tagName.toLowerCase()));

        logger.info("**************************************");
        logger.info("Delete the snapshot");
        logger.info("**************************************");

        resultats = mockMvc.perform(delete("/snapshot/" + tagName.toLowerCase()).session(session)).andDo(print());
        resultats.andExpect(status().isOk());
    }

    @Test()
    public void test011_CreateHelloworldApplicationSnapshot()
            throws Exception {
        logger.info("**************************************");
        logger.info("Deploy a helloworld Application");
        logger.info("**************************************");

        ResultActions resultats =
                mockMvc.perform(MockMvcRequestBuilders.fileUpload("/application/" + applicationName + "/deploy").file(downloadAndPrepareFileToDeploy("helloworld.war",
                        "https://github.com/Treeptik/CloudUnit/releases/download/1.0/helloworld.war")).session(session).contentType(MediaType.MULTIPART_FORM_DATA)).andDo(print());
        Thread.sleep(5000);
        resultats.andExpect(status().is2xxSuccessful());
        String urlToCall = "http://" + applicationName.toLowerCase() + "-johndoe-admin" + domain;
        String contentPage = getUrlContentPage(urlToCall);
        int counter = 0;

        if (release.contains("jboss")||release.contains("wildlfy")) {
            counter = 0;
            while (contentPage.contains("Welcome to WildFly") && counter++ < TestUtils.NB_ITERATION_MAX) {
                contentPage = getUrlContentPage(urlToCall);
                Thread.sleep(1000);
            }
        } else {
            while (contentPage.contains("CloudUnit PaaS")==false || counter++ < 10) {
                contentPage = getUrlContentPage(urlToCall);
                Thread.sleep(1000);
            }
        }
        Assert.assertTrue(contentPage.contains("CloudUnit PaaS"));

        logger.info("**************************************");
        logger.info("Create a snapshot");
        logger.info("**************************************");

        String jsonString =
                "{\"applicationName\":\"" + applicationName + "\", \"tag\":\"" + tagName
                        + "\", \"description\":\"This is a test snapshot\"}";
        logger.info(jsonString);
        resultats =
                mockMvc.perform(post("/snapshot").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString)).andDo(print());
        resultats.andExpect(status().isOk());

        logger.info("**************************************");
        logger.info("List the snapshot");
        logger.info("**************************************");

        resultats = mockMvc.perform(get("/snapshot/list").session(session)).andDo(print());
        resultats.andExpect(status().isOk()).andExpect(jsonPath("$[0].tag").value(tagName.toLowerCase()));

        logger.info("**************************************");
        logger.info("Delete the snapshot");
        logger.info("**************************************");

        resultats = mockMvc.perform(delete("/snapshot/" + tagName).session(session)).andDo(print());
        resultats.andExpect(status().isOk());
    }

    @Test()
    public void test012_CreateAMysqlBasedApplicationSnapshot()
            throws Exception {
        createApplicationSnapshotWithAModuleAndADeployment("mysql-5-5", "pizzashop-mysql", "Pizzas");
    }

    @Test()
    public void test030_CreateAPostGre93BasedApplicationSnapshot()
            throws Exception {
        createApplicationSnapshotWithAModuleAndADeployment("postgresql-9-3", "pizzashop-postgres", "Pizzas");
    }

    @Test()
    public void test031_CreateAPostGre94BasedApplicationSnapshot()
            throws Exception {
        createApplicationSnapshotWithAModuleAndADeployment("postgresql-9-4", "pizzashop-postgres", "Pizzas");
    }

    @Test()
    public void test032_CreateAPostGre95BasedApplicationSnapshot()
            throws Exception {
        createApplicationSnapshotWithAModuleAndADeployment("postgresql-9-5", "pizzashop-postgres", "Pizzas");
    }

    @Test()
    public void test014_CreateAMongoBasedApplicationSnapshot()
            throws Exception {
        createApplicationSnapshotWithAModuleAndADeployment("mongo-2-6", "mongo", "Country");
    }

    @Test()
    public void test015_CreateAndCloneApplicationWithPorts()
            throws Exception {
        logger.info("**************************************");
        logger.info("Open a port");
        logger.info("**************************************");

        String jsonString =
                "{\"applicationName\":\"" + applicationName
                        + "\",\"portToOpen\":\"6115\",\"alias\":\"" + applicationName + "\",\"portNature\":\"web\"}";
        ResultActions resultats =
                this.mockMvc.perform(post("/application/ports")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonString));
        resultats.andExpect(status().isOk()).andDo(print());
        resultats =
                mockMvc.perform(get("/application/" + applicationName).session(session).contentType(MediaType.APPLICATION_JSON)).andDo(print());
        resultats.andExpect(jsonPath("$.portsToOpen[0].port").value(6115));

        logger.info("**************************************");
        logger.info("Create a snapshot");
        logger.info("**************************************");

        jsonString =
                "{\"applicationName\":\"" + applicationName + "\", \"tag\":\"" + tagName
                        + "\", \"description\":\"This is a test snapshot\"}";
        logger.info(jsonString);
        resultats =
                mockMvc.perform(post("/snapshot").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString)).andDo(print());
        resultats.andExpect(status().isOk());

        logger.info("**************************************");
        logger.info("Clone an application : " + applicationName + "cloned");
        logger.info("**************************************");

        jsonString =
                "{\"applicationName\":\"" + applicationName + "cloned" + "\", \"tag\":\"" + tagName
                        + "\", \"description\":\"This is a test snapshot\"}";
        resultats =
                mockMvc.perform(post("/snapshot/clone").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString)).andDo(print());
        resultats.andExpect(status().isOk());
        resultats =
                mockMvc.perform(get("/application/" + applicationName + "cloned").session(session).contentType(MediaType.APPLICATION_JSON)).andDo(print());
        resultats.andExpect(jsonPath("$.portsToOpen[0].port").value(6115));

        logger.info("**************************************");
        logger.info("Delete the cloned application");
        logger.info("**************************************");

        resultats =
                mockMvc.perform(delete("/application/" + applicationName + "cloned").session(session).contentType(MediaType.APPLICATION_JSON));
        resultats.andExpect(status().isOk());


        logger.info("**************************************");
        logger.info("Delete the snapshot");
        logger.info("**************************************");

        resultats = mockMvc.perform(delete("/snapshot/" + tagName).session(session)).andDo(print());
        resultats.andExpect(status().isOk());

    }

    @Test()
    public void test016_CreateAndCloneApplicationWithPortsAndGetAccess()
            throws Exception {
        logger.info("**************************************");
        logger.info("Open a port");
        logger.info("**************************************");

        String jsonString =
                "{\"applicationName\":\"" + applicationName
                        + "\",\"portToOpen\":\"8080\",\"alias\":\"" + applicationName + "\",\"portNature\":\"web\"}";
        ResultActions resultats =
                this.mockMvc.perform(post("/application/ports")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonString));
        resultats.andExpect(status().isOk()).andDo(print());
        resultats =
                mockMvc.perform(get("/application/" + applicationName).session(session).contentType(MediaType.APPLICATION_JSON)).andDo(print());
        resultats.andExpect(jsonPath("$.portsToOpen[0].port").value(8080));

        logger.info("**************************************");
        logger.info("Deploy a helloworld Application");
        logger.info("**************************************");

        resultats =
                mockMvc.perform(fileUpload("/application/" + applicationName + "/deploy").file(downloadAndPrepareFileToDeploy("helloworld.war",
                        "https://github.com/Treeptik/CloudUnit/releases/download/1.0/helloworld.war")).session(session).contentType(MediaType.MULTIPART_FORM_DATA)).andDo(print());
        resultats.andExpect(status().is2xxSuccessful());
        String urlToCall = "http://" + applicationName.toLowerCase() + "-johndoe-forward-8080" + domain;
        String contentPage = getUrlContentPage(urlToCall);
        if (release.contains("jboss")||release.contains("wildlfy")) {
            int counter = 0;
            while (contentPage.contains("Welcome to WildFly") && counter++ < TestUtils.NB_ITERATION_MAX) {
                contentPage = getUrlContentPage(urlToCall);
                Thread.sleep(1000);
            }
        }
        Assert.assertTrue(contentPage.contains("CloudUnit PaaS"));

        logger.info("**************************************");
        logger.info("Create a snapshot");
        logger.info("**************************************");

        jsonString =
                "{\"applicationName\":\"" + applicationName + "\", \"tag\":\"" + tagName
                        + "\", \"description\":\"This is a test snapshot\"}";
        logger.info(jsonString);
        resultats =
                mockMvc.perform(post("/snapshot").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString)).andDo(print());
        resultats.andExpect(status().isOk());

        logger.info("**************************************");
        logger.info("Clone an application : " + applicationName + "cloned");
        logger.info("**************************************");

        jsonString =
                "{\"applicationName\":\"" + applicationName + "cloned" + "\", \"tag\":\"" + tagName
                        + "\", \"description\":\"This is a test snapshot\"}";
        resultats =
                mockMvc.perform(post("/snapshot/clone").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString)).andDo(print());
        resultats.andExpect(status().isOk());
        logger.info("**************************************");
        logger.info("Start the application : " + applicationName + "cloned");
        logger.info("**************************************");

        jsonString = "{\"applicationName\":\"" + applicationName + "cloned" + "\"}";
        resultats =
                this.mockMvc.perform(post("/application/start").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString));
        resultats.andExpect(status().isOk());
        logger.info("**************************************");
        logger.info("check the app keyword : " + applicationName + "cloned");
        logger.info("**************************************");
        resultats =
                mockMvc.perform(get("/application/" + applicationName + "cloned").session(session).contentType(MediaType.APPLICATION_JSON)).andDo(print());
        resultats.andExpect(jsonPath("$.portsToOpen[0].port").value(8080));

        urlToCall = "http://" + applicationName.toLowerCase() + "cloned" + "-johndoe-forward-8080" + domain;
        contentPage = getUrlContentPage(urlToCall);
        if (release.contains("jboss")||release.contains("wildlfy")) {
            int counter = 0;
            while (contentPage.contains("Welcome to WildFly") && counter++ < TestUtils.NB_ITERATION_MAX) {
                contentPage = getUrlContentPage(urlToCall);
                Thread.sleep(1000);
            }
        }
        Assert.assertTrue(contentPage.contains("CloudUnit PaaS"));

        logger.info("***************************************************");
        logger.info("Delete the cloned application before the snapshot");
        logger.info("***************************************************");

        resultats =
                mockMvc.perform(delete("/application/" + applicationName + "cloned").session(session).contentType(MediaType.APPLICATION_JSON));
        resultats.andExpect(status().isOk());

        logger.info("**************************************");
        logger.info("Delete the snapshot");
        logger.info("**************************************");

        resultats = mockMvc.perform(delete("/snapshot/" + tagName).session(session)).andDo(print());
        resultats.andExpect(status().isOk());
    }

    @Test()
    public void test020_CloneASimpleApplicationSnapshot() throws Exception {
        logger.info("**************************************");
        logger.info("Create a snapshot");
        logger.info("**************************************");

        String jsonString =
                "{\"applicationName\":\"" + applicationName + "\", \"tag\":\"" + tagName
                        + "\", \"description\":\"This is a test snapshot\"}";
        logger.info(jsonString);
        ResultActions resultats =
                mockMvc.perform(post("/snapshot").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString)).andDo(print());
        resultats.andExpect(status().isOk());

        logger.info("**************************************");
        logger.info("List the snapshot");
        logger.info("**************************************");

        resultats = mockMvc.perform(get("/snapshot/list").session(session)).andDo(print());
        resultats.andExpect(status().isOk()).andExpect(jsonPath("$[0].tag").value(tagName.toLowerCase()));

        logger.info("**************************************");
        logger.info("Clone an application : " + applicationName + "cloned");
        logger.info("**************************************");

        jsonString =
                "{\"applicationName\":\"" + applicationName + "cloned" + "\", \"tag\":\"" + tagName
                        + "\", \"description\":\"This is a test snapshot\"}";

        resultats =
                mockMvc.perform(post("/snapshot/clone").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString)).andDo(print());
        resultats.andExpect(status().isOk());

        resultats = mockMvc.perform(get("/application/" + applicationName + "cloned").session(session).contentType(MediaType.APPLICATION_JSON));
        resultats.andExpect(jsonPath("origin").value(tagName.toLowerCase()));

        logger.info("**************************************");
        logger.info("Delete the cloned application");
        logger.info("**************************************");

        resultats =
                mockMvc.perform(delete("/application/" + applicationName + "cloned").session(session).contentType(MediaType.APPLICATION_JSON));
        resultats.andExpect(status().isOk());

        logger.info("**************************************");
        logger.info("Delete the snapshot");
        logger.info("**************************************");

        resultats = mockMvc.perform(delete("/snapshot/" + tagName).session(session)).andDo(print());
        resultats.andExpect(status().isOk());
    }

    @Test
    public void test021_CloneAHelloworldApplicationSnapshot()
            throws Exception {
        logger.info("**************************************");
        logger.info("Deploy a helloworld Application");
        logger.info("**************************************");

        ResultActions resultats =
                mockMvc.perform(fileUpload("/application/" + applicationName + "/deploy").file(downloadAndPrepareFileToDeploy("helloworld.war",
                        "https://github.com/Treeptik/CloudUnit/releases/download/1.0/helloworld.war")).session(session).contentType(MediaType.MULTIPART_FORM_DATA)).andDo(print());
        resultats.andExpect(status().is2xxSuccessful());
        String urlToCall = "http://" + applicationName.toLowerCase() + "-johndoe-admin" + domain;
        String contentPage = getUrlContentPage(urlToCall);
        if (release.contains("jboss")||release.contains("wildlfy")) {
            int counter = 0;
            while (contentPage.contains("Welcome to WildFly") && counter++ < TestUtils.NB_ITERATION_MAX) {
                contentPage = getUrlContentPage(urlToCall);
                Thread.sleep(1000);
            }
        }
        Assert.assertTrue(contentPage.contains("CloudUnit PaaS"));

        logger.info("**************************************");
        logger.info("Create a snapshot");
        logger.info("**************************************");

        String jsonString =
                "{\"applicationName\":\"" + applicationName + "\", \"tag\":\"" + tagName
                        + "\", \"description\":\"This is a test snapshot\"}";
        logger.info(jsonString);
        resultats =
                mockMvc.perform(post("/snapshot").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString)).andDo(print());
        resultats.andExpect(status().isOk());

        logger.info("**************************************");
        logger.info("List the snapshot");
        logger.info("**************************************");

        resultats = mockMvc.perform(get("/snapshot/list").session(session)).andDo(print());
        resultats.andExpect(status().isOk()).andExpect(jsonPath("$[0].tag").value(tagName.toLowerCase()));

        logger.info("**************************************");
        logger.info("Clone an application : " + applicationName + "cloned");
        logger.info("**************************************");

        jsonString =
                "{\"applicationName\":\"" + applicationName + "cloned" + "\", \"tag\":\"" + tagName
                        + "\", \"description\":\"This is a test snapshot\"}";

        resultats =
                mockMvc.perform(post("/snapshot/clone").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString)).andDo(print());
        resultats.andExpect(status().isOk());

        logger.info("**************************************");
        logger.info("Start the application : " + applicationName + "cloned");
        logger.info("**************************************");

        jsonString = "{\"applicationName\":\"" + applicationName + "cloned" + "\"}";
        resultats =
                this.mockMvc.perform(post("/application/start").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString));
        resultats.andExpect(status().isOk());

        logger.info("**************************************");
        logger.info("Check the keyword of " + applicationName + "cloned");
        logger.info("**************************************");

        urlToCall = "http://" + applicationName.toLowerCase() + "cloned" + "-johndoe-admin" + domain;
        contentPage = getUrlContentPage(urlToCall);
        if (release.contains("jboss")||release.contains("wildlfy")) {
            int counter = 0;
            while (contentPage.contains("Welcome to WildFly") && counter++ < TestUtils.NB_ITERATION_MAX) {
                contentPage = getUrlContentPage(urlToCall);
                Thread.sleep(1000);
            }
        }
        Assert.assertTrue(contentPage.contains("CloudUnit PaaS"));

        logger.info("**************************************");
        logger.info("Delete the cloned application");
        logger.info("**************************************");

        resultats =
                mockMvc.perform(delete("/application/" + applicationName + "cloned").session(session).contentType(MediaType.APPLICATION_JSON));
        resultats.andExpect(status().isOk());

        logger.info("**************************************");
        logger.info("Delete the snapshot");
        logger.info("**************************************");

        resultats = mockMvc.perform(delete("/snapshot/" + tagName).session(session)).andDo(print());
        resultats.andExpect(status().isOk());
    }

    @Test
    public void test022_CloneAMysqlBasedApplicationSnapshot()
            throws Exception {
        cloneASnapshotWithApplicationWithModuleAndADeployment("mysql-5-5", "pizzashop-mysql", "Pizzas");
    }

    @Test()
    public void test023_CloneAPostGresBasedApplicationSnapshot()
            throws Exception {
        cloneASnapshotWithApplicationWithModuleAndADeployment("postgresql-9-3", "pizzashop-postgres", "Pizzas");
    }

    @Test()
    public void test024_CloneAMongoBasedApplicationSnapshot()
            throws Exception {
        cloneASnapshotWithApplicationWithModuleAndADeployment("mongo-2-6", "mongo", "Country");
    }

    @Test()
    public void test030_ChangeJvmOptionsApplicationTest()
            throws Exception {
        logger.info("Change JVM Options !");

        String jsonString =
                "{\"applicationName\":\"" + applicationName
                        + "\",\"jvmMemory\":\"1024\",\"jvmOptions\":\"-Dkey1=value1\",\"jvmRelease\":\"jdk1.8.0_25\"}";
        ResultActions resultats =
                mockMvc.perform(put("/server/configuration/jvm").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString));
        resultats.andExpect(status().isOk());

        resultats =
                mockMvc.perform(get("/application/" + applicationName).session(session).contentType(MediaType.APPLICATION_JSON));
        resultats.andExpect(jsonPath("$.servers[0].jvmMemory").value(1024)).andExpect(jsonPath(
                "$.servers[0].jvmRelease").value("jdk1.8.0_25")).andExpect(jsonPath(
                "$.servers[0].jvmOptions").value("-Dkey1=value1"));

        logger.info("**************************************");
        logger.info("Create a snapshot");
        logger.info("**************************************");

        jsonString =
                "{\"applicationName\":\"" + applicationName + "\", \"tag\":\"" + tagName
                        + "\", \"description\":\"This is a test snapshot\"}";
        logger.info(jsonString);
        resultats =
                mockMvc.perform(post("/snapshot").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString)).andDo(print());
        resultats.andExpect(status().isOk());

        logger.info("**************************************");
        logger.info("List the snapshot");
        logger.info("**************************************");

        resultats = mockMvc.perform(get("/snapshot/list").session(session)).andDo(print());
        resultats.andExpect(status().isOk()).andExpect(jsonPath("$[0].tag").value(tagName.toLowerCase()));

        logger.info("**************************************");
        logger.info("Clone an application : " + applicationName + "cloned");
        logger.info("**************************************");

        jsonString =
                "{\"applicationName\":\"" + applicationName + "cloned" + "\", \"tag\":\"" + tagName
                        + "\", \"description\":\"This is a test snapshot\"}";

        resultats =
                mockMvc.perform(post("/snapshot/clone").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString)).andDo(print());
        resultats.andExpect(status().isOk());

        logger.info("**************************************");
        logger.info("Start the application : " + applicationName + "cloned");
        logger.info("**************************************");

        jsonString = "{\"applicationName\":\"" + applicationName + "cloned" + "\"}";
        resultats =
                this.mockMvc.perform(post("/application/start").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString));
        resultats.andExpect(status().isOk());
        resultats =
                mockMvc.perform(get("/application/" + applicationName + "cloned").session(session).contentType(MediaType.APPLICATION_JSON));
        resultats.andExpect(jsonPath("$.servers[0].jvmMemory").value(1024)).andExpect(jsonPath(
                "$.servers[0].jvmRelease").value("jdk1.8.0_25")).andExpect(jsonPath(
                "$.servers[0].jvmOptions").value("-Dkey1=value1"));

        logger.info("**************************************");
        logger.info("Delete the cloned application");
        logger.info("**************************************");

        resultats =
                mockMvc.perform(delete("/application/" + applicationName + "cloned").session(session).contentType(MediaType.APPLICATION_JSON));
        resultats.andExpect(status().isOk());

        logger.info("**************************************");
        logger.info("Delete the snapshot");
        logger.info("**************************************");

        resultats = mockMvc.perform(delete("/snapshot/" + tagName).session(session)).andDo(print());
        resultats.andExpect(status().isOk());

    }


    @Test()
    public void test040_CreateSimpleApplicationSnapshotWithNonAlphaNumericSyntaxName()
            throws Exception {
        String nonAlphaNum = "NON-ALPHA-NUM";

        logger.info("**************************************");
        logger.info("Create a snapshot with a non-alpha numeric syntax : " + nonAlphaNum );
        logger.info("**************************************");

        String jsonString =
                "{\"applicationName\":\"" + applicationName + "\", \"tag\":\"" + nonAlphaNum
                        + "\", \"description\":\"This is a test snapshot\"}";
        logger.info(jsonString);
        ResultActions resultats =
                mockMvc.perform(post("/snapshot").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString)).andDo(print());
        resultats.andExpect(status().is2xxSuccessful());
    }


    @Test()
    public void test041_CreateSimpleApplicationSnapshotWithAccentName()
            throws Exception {
        String accentTagName = "1234àéèîù";
        String deAccentTagName = "1234aeeiu";

        logger.info("**************************************");
        logger.info("Create a snapshot with a accent name : " + accentTagName);
        logger.info("**************************************");

        String jsonString =
                "{\"applicationName\":\"" + applicationName + "\", \"tag\":\"" + accentTagName
                        + "\", \"description\":\"This is a test snapshot\"}";
        logger.info(jsonString);
        ResultActions resultats =
                mockMvc.perform(post("/snapshot").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString)).andDo(print());
        resultats.andExpect(status().isOk());

        logger.info("**************************************");
        logger.info("List the snapshot");
        logger.info("**************************************");

        resultats = mockMvc.perform(get("/snapshot/list").session(session)).andDo(print());
        resultats.andExpect(status().isOk()).andExpect(jsonPath("$[0].tag").value(deAccentTagName.toLowerCase()));

        logger.info("**************************************");
        logger.info("Delete the snapshot");
        logger.info("**************************************");

        resultats = mockMvc.perform(delete("/snapshot/" + deAccentTagName).session(session)).andDo(print());
        resultats.andExpect(status().isOk());
    }


    private void cloneASnapshotWithApplicationWithModuleAndADeployment(String module, String appName,
                                                                       String keywordIntoPage)
            throws Exception {
        logger.info("**************************************");
        logger.info("Add the module");
        logger.info("**************************************");

        String jsonString = "{\"applicationName\":\"" + applicationName + "\", \"imageName\":\"" + module + "\"}";
        ResultActions resultats =
                mockMvc.perform(post("/module").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString)).andDo(print());
        resultats.andExpect(status().isOk());

        // Stop the application
        jsonString = "{\"applicationName\":\"" + applicationName + "\"}";
        resultats = mockMvc.perform(post("/application/stop").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString));
        resultats.andExpect(status().isOk());

        // Start the application
        jsonString = "{\"applicationName\":\"" + applicationName + "\"}";
        resultats = mockMvc.perform(post("/application/start").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString));
        resultats.andExpect(status().isOk());


        logger.info("**************************************");
        logger.info("Deploy a helloworld Application");
        logger.info("**************************************");

        logger.info("Deploy an " + module + " based application");
        resultats =
                mockMvc.perform(MockMvcRequestBuilders.fileUpload("/application/" + applicationName + "/deploy").file(downloadAndPrepareFileToDeploy(appName
                                + ".war",
                        "https://github.com/Treeptik/CloudUnit/releases/download/1.0/"
                                + appName + ".war")).session(session).contentType(MediaType.MULTIPART_FORM_DATA)).andDo(print());
        // test the application content page
        resultats.andExpect(status().is2xxSuccessful());
        String urlToCall = "http://" + applicationName.toLowerCase() + "-johndoe-admin" + domain;
        String contentPage = getUrlContentPage(urlToCall);
        if (release.contains("jboss")||release.contains("wildfly")) {
            int counter = 0;
            while (contentPage.contains("Welcome to WildFly") && counter++ < TestUtils.NB_ITERATION_MAX) {
                contentPage = getUrlContentPage(urlToCall);
                Thread.sleep(1000);
            }
        }
        Assert.assertTrue(contentPage.contains(keywordIntoPage));

        logger.info("**************************************");
        logger.info("Create a snapshot");
        logger.info("**************************************");

        jsonString =
                "{\"applicationName\":\"" + applicationName + "\", \"tag\":\"" + tagName
                        + "\", \"description\":\"This is a test snapshot\"}";
        logger.info(jsonString);
        resultats =
                mockMvc.perform(post("/snapshot").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString)).andDo(print());
        resultats.andExpect(status().isOk());

        logger.info("**************************************");
        logger.info("List the snapshot");
        logger.info("**************************************");

        resultats = mockMvc.perform(get("/snapshot/list").session(session)).andDo(print());
        resultats.andExpect(status().isOk()).andExpect(jsonPath("$[0].tag").value(tagName.toLowerCase()));

        logger.info("**************************************");
        logger.info("Clone an application : " + applicationName + "cloned");
        logger.info("**************************************");

        jsonString =
                "{\"applicationName\":\"" + applicationName + "cloned" + "\", \"tag\":\"" + tagName
                        + "\", \"description\":\"This is a test snapshot\"}";

        resultats =
                mockMvc.perform(post("/snapshot/clone").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString)).andDo(print());
        resultats.andExpect(status().isOk());

        logger.info("**************************************");
        logger.info("Start the application : " + applicationName + "cloned");
        logger.info("**************************************");

        jsonString = "{\"applicationName\":\"" + applicationName + "cloned" + "\"}";
        resultats =
                this.mockMvc.perform(post("/application/start").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString));
        resultats.andExpect(status().isOk());

        logger.info("**************************************");
        logger.info("Check the contentPage of " + applicationName + "cloned");
        logger.info("**************************************");

        urlToCall = "http://" + applicationName.toLowerCase() + "cloned" + "-johndoe-admin" + domain;
        if (release.contains("jboss")||release.contains("wildlfy")) {
            int counter = 0;
            contentPage = getUrlContentPage(urlToCall);
            while (contentPage.contains("Welcome to WildFly") && counter++ < TestUtils.NB_ITERATION_MAX) {
                contentPage = getUrlContentPage(urlToCall);
                Thread.sleep(1000);
            }
        }
        Assert.assertTrue(contentPage.contains(keywordIntoPage));

        logger.info("**************************************");
        logger.info("Delete the cloned application");
        logger.info("**************************************");

        resultats =
                mockMvc.perform(delete("/application/" + applicationName + "cloned").session(session).contentType(MediaType.APPLICATION_JSON));
        resultats.andExpect(status().isOk());

        logger.info("**************************************");
        logger.info("Delete the snapshot");
        logger.info("**************************************");

        resultats = mockMvc.perform(delete("/snapshot/" + tagName).session(session)).andDo(print());
        resultats.andExpect(status().isOk());
    }

    private void createApplicationSnapshotWithAModuleAndADeployment(String module, String appName, String keywordIntoPage)
            throws Exception {
        logger.info("**************************************");
        logger.info("Add the module");
        logger.info("**************************************");

        String jsonString = "{\"applicationName\":\"" + applicationName + "\", \"imageName\":\"" + module + "\"}";
        ResultActions resultats =
                mockMvc.perform(post("/module").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString)).andDo(print());
        resultats.andExpect(status().isOk());

        // Stop the application
        jsonString = "{\"applicationName\":\"" + applicationName + "\"}";
        resultats = mockMvc.perform(post("/application/stop").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString));
        resultats.andExpect(status().isOk());

        // Start the application
        jsonString = "{\"applicationName\":\"" + applicationName + "\"}";
        resultats = mockMvc.perform(post("/application/start").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString));
        resultats.andExpect(status().isOk());

        logger.info("**************************************");
        logger.info("Deploy a helloworld Application");
        logger.info("**************************************");

        logger.info("Deploy an " + module + " based application");
        resultats =
                mockMvc.perform(MockMvcRequestBuilders.fileUpload("/application/" + applicationName + "/deploy").file(downloadAndPrepareFileToDeploy(appName
                                + ".war",
                        "https://github.com/Treeptik/CloudUnit/releases/download/1.0/"
                                + appName
                                + ".war")).session(session).contentType(MediaType.MULTIPART_FORM_DATA)).andDo(print());
        // test the application content page
        resultats.andExpect(status().is2xxSuccessful());
        String urlToCall = "http://" + applicationName.toLowerCase() + "-johndoe-admin" + domain;
        String contentPage = getUrlContentPage(urlToCall);
        int counter = 0;
        if (release.contains("jboss")||release.contains("wildfly")) {
            while (!contentPage.contains("Welcome to WildFly") && counter++ < TestUtils.NB_ITERATION_MAX) {
                contentPage = getUrlContentPage(urlToCall);
                Thread.sleep(1000);
            }
        } else {
            while (!contentPage.contains(keywordIntoPage) || counter++ < 10) {
                contentPage = getUrlContentPage(urlToCall);
                Thread.sleep(1000);
            }
        }
        Assert.assertTrue(contentPage.contains(keywordIntoPage));

        logger.info("**************************************");
        logger.info("Create a snapshot");
        logger.info("**************************************");

        jsonString =
                "{\"applicationName\":\"" + applicationName + "\", \"tag\":\"" + tagName
                        + "\", \"description\":\"This is a test snapshot\"}";
        logger.info(jsonString);
        resultats =
                mockMvc.perform(post("/snapshot").session(session).contentType(MediaType.APPLICATION_JSON).content(jsonString)).andDo(print());
        resultats.andExpect(status().isOk());

        logger.info("**************************************");
        logger.info("List the snapshot");
        logger.info("**************************************");

        resultats = mockMvc.perform(get("/snapshot/list").session(session)).andDo(print());
        resultats.andExpect(status().isOk()).andExpect(jsonPath("$[0].tag").value(tagName.toLowerCase()));

        logger.info("**************************************");
        logger.info("Delete the snapshot");
        logger.info("**************************************");

        resultats = mockMvc.perform(delete("/snapshot/" + tagName).session(session)).andDo(print());
        resultats.andExpect(status().isOk());
    }

}