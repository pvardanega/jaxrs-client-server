package net.awired.ajsl.ws.rest;

import static org.fest.assertions.api.Assertions.assertThat;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.xml.bind.annotation.XmlRootElement;
import org.junit.Test;

public class RestContextTest {

    private String url = "http://127.0.0.1:8080";
    private RestBuilder context = new RestBuilder();

    @XmlRootElement
    public static class User {
        public String firstname = "Arnaud";
        public String lastname = "Lemaire";
    }

    @Path("/")
    private interface UsersResource {
        @GET
        User getUser();
    }

    public class UsersService implements UsersResource {
        @Override
        public User getUser() {
            return new User();
        }
    }

    @Test
    public void should_transfert_default() throws Exception {
        context.buildServer(url, new UsersService());
        UsersResource resource = context.buildClient(UsersResource.class, url);

        User user = resource.getUser();

        assertThat(user).isEqualsToByComparingFields(new User());
    }

    @Test
    public void should_transfert_xml() throws Exception {
        context.buildServer(url, new UsersService());
        UsersResource resource = context.buildClient(UsersResource.class, url, new RestSession().asXml());

        User user = resource.getUser();

        assertThat(user).isEqualsToByComparingFields(new User());
    }

    @Test
    public void should_transfert_json() throws Exception {
        context.buildServer(url, new UsersService());
        UsersResource resource = context.buildClient(UsersResource.class, url, new RestSession().asJson());

        User user = resource.getUser();

        assertThat(user).isEqualsToByComparingFields(new User());
    }
}
