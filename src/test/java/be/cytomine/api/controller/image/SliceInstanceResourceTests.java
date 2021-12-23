package be.cytomine.api.controller.image;

import be.cytomine.BasicInstanceBuilder;
import be.cytomine.CytomineCoreApplication;
import be.cytomine.domain.image.AbstractSlice;
import be.cytomine.domain.image.SliceInstance;
import be.cytomine.repository.meta.PropertyRepository;
import be.cytomine.utils.JsonObject;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CytomineCoreApplication.class)
@AutoConfigureMockMvc
@WithMockUser(username = "superadmin")
@Transactional
public class SliceInstanceResourceTests {

    @Autowired
    private EntityManager em;

    @Autowired
    private BasicInstanceBuilder builder;

    @Autowired
    private MockMvc restSliceInstanceControllerMockMvc;

    @Autowired
    private PropertyRepository propertyRepository;

    private static WireMockServer wireMockServer = new WireMockServer(8888);
    
    @BeforeAll
    public static void beforeAll() {
        wireMockServer.start();
    }

    @AfterAll
    public static void afterAll() {
        try {
            wireMockServer.stop();
        } catch (Exception e) {}
    }

    @Test
    @Transactional
    public void list_slice_instance_by_image_instance() throws Exception {
        SliceInstance sliceInstance = builder.given_a_slice_instance();

        restSliceInstanceControllerMockMvc.perform(get("/api/imageinstance/{id}/sliceinstance.json", sliceInstance.getImage().getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection[?(@.id=="+sliceInstance.getId()+")]").exists());
    }

    @Test
    @Transactional
    public void get_an_slice_instance() throws Exception {
        SliceInstance image = given_test_slice_instance();

        restSliceInstanceControllerMockMvc.perform(get("/api/sliceinstance/{id}.json", image.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(image.getId().intValue()))
                .andExpect(jsonPath("$.class").value("be.cytomine.domain.image.SliceInstance"))
                .andExpect(jsonPath("$.created").exists())
                .andExpect(jsonPath("$.image").value(image.getImage().getId()))
                .andExpect(jsonPath("$.mime").hasJsonPath())
                .andExpect(jsonPath("$.imageServerUrl").value("http://localhost:8888"))
                .andExpect(jsonPath("$.baseSlice").hasJsonPath())
                .andExpect(jsonPath("$.path").hasJsonPath())
                .andExpect(jsonPath("$.zStack").hasJsonPath())
                .andExpect(jsonPath("$.rank").hasJsonPath())
                .andExpect(jsonPath("$.time").hasJsonPath())
                .andExpect(jsonPath("$.updated").hasJsonPath())
                .andExpect(jsonPath("$.uploadedFile").hasJsonPath());

//        {
//            "image":39965,
//                "created":"1636702046099",
//                "mime":"openslide\/mrxs",
//                "channel":0,
//                "project":39933,
//                "imageServerUrl":"http:\/\/localhost-ims",
//                "baseSlice":32202,
//                "path":"\/data\/images\/58\/1636379100999\/CMU-2\/CMU-2.mrxs",
//                "deleted":null,
//                "zStack":0,
//                "rank":0,
//                "id":39966,
//                "time":0,
//                "class":"be.cytomine.image.SliceInstance",
//                "updated":null,
//                "uploadedFile":29598
//        }



    }

    @Test
    @Transactional
    public void get_an_slice_instance_not_exist() throws Exception {
        restSliceInstanceControllerMockMvc.perform(get("/api/sliceinstance/{id}.json", 0))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors.message").exists());
    }


    @Test
    @Transactional
    public void get_an_slice_instance_with_coordinates() throws Exception {
        SliceInstance image = given_test_slice_instance();

        restSliceInstanceControllerMockMvc.perform(get("/api/imageinstance/{id}/{channel}/{zStack}/{time}/sliceinstance.json",
                        image.getImage().getId(), image.getBaseSlice().getChannel(), image.getBaseSlice().getZStack(), image.getBaseSlice().getTime()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(image.getId().intValue()));
    }

    @Test
    @Transactional
    public void add_valid_slice_instance() throws Exception {
        SliceInstance sliceInstance = builder.given_a_not_persisted_slice_instance();
        restSliceInstanceControllerMockMvc.perform(post("/api/sliceinstance.json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sliceInstance.toJSON()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.printMessage").value(true))
                .andExpect(jsonPath("$.callback").exists())
                .andExpect(jsonPath("$.callback.sliceinstanceID").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.command").exists())
                .andExpect(jsonPath("$.sliceinstance.id").exists());

    }

    @Test
    @Transactional
    public void edit_valid_slice_instance() throws Exception {
        SliceInstance sliceInstance = builder.given_a_slice_instance();
        JsonObject jsonObject = sliceInstance.toJsonObject();
        restSliceInstanceControllerMockMvc.perform(put("/api/sliceinstance/{id}.json", sliceInstance.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonObject.toJsonString()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.printMessage").value(true))
                .andExpect(jsonPath("$.callback").exists())
                .andExpect(jsonPath("$.callback.sliceinstanceID").exists())
                .andExpect(jsonPath("$.callback.method").value("be.cytomine.EditSliceInstanceCommand"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.command").exists())
                .andExpect(jsonPath("$.sliceinstance.id").exists());


    }


    @Test
    @Transactional
    public void delete_slice_instance() throws Exception {
        SliceInstance sliceInstance = builder.given_a_slice_instance();
        restSliceInstanceControllerMockMvc.perform(delete("/api/sliceinstance/{id}.json", sliceInstance.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.printMessage").value(true))
                .andExpect(jsonPath("$.callback").exists())
                .andExpect(jsonPath("$.callback.sliceinstanceID").exists())
                .andExpect(jsonPath("$.callback.method").value("be.cytomine.DeleteSliceInstanceCommand"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.command").exists())
                .andExpect(jsonPath("$.sliceinstance.id").exists());


    }

    @Test
    @Transactional
    public void get_slice_instance_thumb() throws Exception {
        SliceInstance image = given_test_slice_instance();

        configureFor("localhost", 8888);
        stubFor(get(urlEqualTo("/slice/thumb.png?fif=%2Fdata%2Fimages%2F" + builder.given_superadmin().getId()+ "%2F1636379100999%2FCMU-2%2FCMU-2.mrxs&mimeType=openslide%2Fmrxs&maxSize=512"))
                .willReturn(
                        aResponse().withBody(new byte[]{0,1,2,3})
                )
        );

        MvcResult mvcResult = restSliceInstanceControllerMockMvc.perform(get("/api/sliceinstance/{id}/thumb.png?maxSize=512", image.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();
        List<LoggedRequest> all = wireMockServer.findAll(RequestPatternBuilder.allRequests());
        assertThat(mvcResult.getResponse().getContentAsByteArray()).isEqualTo(new byte[]{0,1,2,3});
    }

    @Test
    @Transactional
    public void get_slice_instance_thumb_if_image_not_exist() throws Exception {
        restSliceInstanceControllerMockMvc.perform(get("/api/sliceinstance/{id}/thumb.png", 0))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors").exists());
    }


    @Test
    @Transactional
    public void get_slice_instance_crop() throws Exception {
        SliceInstance image = given_test_slice_instance();

        configureFor("localhost", 8888);


        String url = "/slice/crop.png?fif=%2Fdata%2Fimages%2F"+builder.given_superadmin().getId()+"%2F1636379100999%2FCMU-2%2FCMU-2.mrxs&mimeType=openslide%2Fmrxs&topLeftX=1&topLeftY=50&width=49&height=49&location=POLYGON+%28%281+1%2C+50+10%2C+50+50%2C+10+50%2C+1+1%29%29&imageWidth=109240&imageHeight=220696&type=crop";
        stubFor(get(urlEqualTo(url))
                .willReturn(
                        aResponse().withBody(new byte[]{99})
                )
        );

        MvcResult mvcResult = restSliceInstanceControllerMockMvc.perform(get("/api/sliceinstance/{id}/crop.png", image.getId())
                        .param("location", "POLYGON((1 1,50 10,50 50,10 50,1 1))"))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();
        List<LoggedRequest> all = wireMockServer.findAll(RequestPatternBuilder.allRequests());
        AssertionsForClassTypes.assertThat(mvcResult.getResponse().getContentAsByteArray()).isEqualTo(new byte[]{99});
    }

    @Test
    @Transactional
    public void get_slice_instance_window() throws Exception {
        SliceInstance image = given_test_slice_instance();

        configureFor("localhost", 8888);
        String url = "/slice/crop.png?fif=%2Fdata%2Fimages%2F" + builder.given_superadmin().getId() + "%2F1636379100999%2FCMU-2%2FCMU-2.mrxs&mimeType=openslide%2Fmrxs&topLeftX=10&topLeftY=220676&width=30&height=40&imageWidth=109240&imageHeight=220696&type=crop";
        stubFor(get(urlEqualTo(url))
                .willReturn(
                        aResponse().withBody(new byte[]{123})
                )
        );

        MvcResult mvcResult = restSliceInstanceControllerMockMvc.perform(get("/api/sliceinstance/{id}/window-10-20-30-40.png", image.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();
        List<LoggedRequest> all = wireMockServer.findAll(RequestPatternBuilder.allRequests());
        AssertionsForClassTypes.assertThat(mvcResult.getResponse().getContentAsByteArray()).isEqualTo(new byte[]{123});


        restSliceInstanceControllerMockMvc.perform(get("/api/sliceinstance/{id}/window_url-10-20-30-40.jpg", image.getId()))
                .andDo(print())
                .andExpect(jsonPath("$.url").value("http://localhost:8888/slice/crop.jpg?fif=%2Fdata%2Fimages%2F"+builder.given_superadmin().getId()+"%2F1636379100999%2FCMU-2%2FCMU-2.mrxs&mimeType=openslide%2Fmrxs&topLeftX=10&topLeftY=220676&width=30&height=40&imageWidth=109240&imageHeight=220696&type=crop"))
                .andExpect(status().isOk());

    }


    @Test
    @Transactional
    public void get_slice_instance_camera() throws Exception {
        SliceInstance image = given_test_slice_instance();


        configureFor("localhost", 8888);
        String url = "/slice/crop.png?fif=%2Fdata%2Fimages%2F" + builder.given_superadmin().getId() + "%2F1636379100999%2FCMU-2%2FCMU-2.mrxs&mimeType=openslide%2Fmrxs&topLeftX=10&topLeftY=220676&width=30&height=40&imageWidth=109240&imageHeight=220696&type=crop";
        stubFor(get(urlEqualTo(url))
                .willReturn(
                        aResponse().withBody(new byte[]{123})
                )
        );

        MvcResult mvcResult = restSliceInstanceControllerMockMvc.perform(get("/api/sliceinstance/{id}/camera-10-20-30-40.png", image.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();
        List<LoggedRequest> all = wireMockServer.findAll(RequestPatternBuilder.allRequests());
        AssertionsForClassTypes.assertThat(mvcResult.getResponse().getContentAsByteArray()).isEqualTo(new byte[]{123});


        restSliceInstanceControllerMockMvc.perform(get("/api/sliceinstance/{id}/camera_url-10-20-30-40.jpg", image.getId()))
                .andDo(print())
                .andExpect(jsonPath("$.url").value("http://localhost:8888/slice/crop.jpg?fif=%2Fdata%2Fimages%2F"+builder.given_superadmin().getId()+"%2F1636379100999%2FCMU-2%2FCMU-2.mrxs&mimeType=openslide%2Fmrxs&topLeftX=10&topLeftY=220676&width=30&height=40&imageWidth=109240&imageHeight=220696&type=crop"))
                .andExpect(status().isOk());

    }

    private SliceInstance given_test_slice_instance() {
        AbstractSlice image = builder.given_an_abstract_slice();
        image.setMime(builder.given_a_mime("openslide/mrxs"));
        image.getImage().setWidth(109240);
        image.getImage().setHeight(220696);
        image.getUploadedFile().getImageServer().setBasePath("/data/images");
        image.getUploadedFile().getImageServer().setUrl("http://localhost:8888");
        image.getUploadedFile().setFilename("1636379100999/CMU-2/CMU-2.mrxs");
        image.getUploadedFile().setContentType("openslide/mrxs");
        SliceInstance sliceInstance = builder.given_a_slice_instance(builder.given_an_image_instance(image.getImage(), builder.given_a_project()),image);
        return sliceInstance;
    }

}