package net.awired.ajsl.ws.rest;

import static java.util.Arrays.asList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.HttpHeaders;
import net.awired.ajsl.ws.resource.mapper.NotFoundExceptionMapper;
import net.awired.ajsl.ws.resource.mapper.UpdateExceptionMapper;
import net.awired.ajsl.ws.resource.mapper.ValidationExceptionMapper;
import net.awired.ajsl.ws.resource.mapper.generic.GenericExceptionMapper;
import net.awired.ajsl.ws.resource.mapper.generic.GenericResponseExceptionMapper;
import net.awired.ajsl.ws.resource.mapper.generic.RuntimeExceptionMapper;
import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.jaxrs.AbstractJAXRSFactoryBean;
import org.apache.cxf.jaxrs.JAXRSBindingFactory;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.Client;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.message.Message;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;

public class RestBuilder {

    private boolean logExchange = true;
    private GenericResponseExceptionMapper responseExceptionMapper;
    private LoggingInInterceptor inLogger;
    private LoggingOutInterceptor outLogger;
    private JacksonJsonProvider jacksonJsonProvider;
    private final List<Object> providers = new ArrayList<Object>();
    private final List<Interceptor<? extends Message>> inInterceptors = new ArrayList<Interceptor<? extends Message>>();
    private final List<Interceptor<? extends Message>> outInterceptors = new ArrayList<Interceptor<? extends Message>>();

    public RestBuilder() {
        inLogger = new LoggingInInterceptor();
        inLogger.setPrettyLogging(true);
        inLogger.setOutputLocation("<stdout>");
        outLogger = new LoggingOutInterceptor();
        outLogger.setPrettyLogging(true);
        outLogger.setOutputLocation("<stderr>");

        ObjectMapper restfullObjectMapper = new ObjectMapper().setSerializationInclusion(Include.NON_NULL);
        AnnotationIntrospector pair = new AnnotationIntrospectorPair(new JacksonAnnotationIntrospector(),
                new JaxbAnnotationIntrospector(TypeFactory.defaultInstance()));
        restfullObjectMapper.setAnnotationIntrospector(pair);
        jacksonJsonProvider = new JacksonJsonProvider();
        jacksonJsonProvider.setMapper(restfullObjectMapper);
        providers.add(jacksonJsonProvider);
    }

    public void withExceptionMapper() {
        responseExceptionMapper = new GenericResponseExceptionMapper(jacksonJsonProvider);
        providers.add(responseExceptionMapper);
        providers.add(new GenericExceptionMapper());
        providers.add(new NotFoundExceptionMapper());
        providers.add(new UpdateExceptionMapper());
        providers.add(new RuntimeExceptionMapper());
        providers.add(new ValidationExceptionMapper());
    }

    public void addProvider(Object provider) {
        this.providers.add(provider);
    }

    public void addAllProvider(Collection<Object> providers) {
        this.providers.addAll(providers);
    }

    public void addAllInInterceptor(Collection<Interceptor<? extends Message>> inInterceptors) {
        this.inInterceptors.addAll(inInterceptors);
    }

    public void addAllOutInterceptor(Collection<Interceptor<? extends Message>> outInterceptors) {
        this.outInterceptors.addAll(outInterceptors);
    }

    public void addInInterceptor(Interceptor<? extends Message> inInterceptor) {
        this.inInterceptors.add(inInterceptor);
    }

    public void addOutInterceptor(Interceptor<? extends Message> outInterceptor) {
        this.outInterceptors.add(outInterceptor);
    }

    public <T> T buildClient(Class<T> clazz, String connectionUrl) {
        return buildClient(clazz, connectionUrl, new RestSession());
    }

    public <T> T buildClient(Class<T> clazz, String connectionUrl, RestSession session) {
        JAXRSClientFactoryBean cf = new JAXRSClientFactoryBean();
        prepareFactory(connectionUrl, cf);
        cf.setResourceClass(clazz);
        BindingFactoryManager manager = cf.getBus().getExtension(BindingFactoryManager.class);
        JAXRSBindingFactory factory = new JAXRSBindingFactory();
        factory.setBus(cf.getBus());
        manager.registerBindingFactory(JAXRSBindingFactory.JAXRS_BINDING_ID, factory);
        T service = cf.create(clazz);
        if (session != null) {
            prepareClient(session, WebClient.client(service));
        }
        return service;
    }

    public Server buildServer(String listenUrl, Object resource) {
        return buildServer(listenUrl, asList(resource));
    }

    public Server buildServer(String listenUrl, Collection<?> resources) {
        JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
        prepareFactory(listenUrl, sf);

        sf.setServiceBeans(new ArrayList<Object>(resources));
        //        factory.setResourceClasses(resources);
        sf.setAddress(listenUrl);
        return sf.create();
    }

    private void prepareFactory(String address, AbstractJAXRSFactoryBean f) {
        f.setProviders(providers);

        if (logExchange) {
            f.getInInterceptors().add(inLogger);
            f.getOutInterceptors().add(outLogger);
        }

        f.getInInterceptors().addAll(inInterceptors);
        f.getOutInterceptors().addAll(outInterceptors);

        f.setAddress(address);
    }

    protected void prepareClient(RestSession session, Client client) {
        if (session.getAcceptType() != null) {
            client.accept(session.getAcceptType());
        }
        if (session.getContentType() != null) {
            client.type(session.getContentType());
        }
        if (session.getSessionId() != null) {
            client.header(HttpHeaders.COOKIE, "JSESSIONID=" + session.getSessionId());
            //        headers.put("Cookie2", "$Version=1");
        }
        if (session.getToken() != null) {
            client.header(HttpHeaders.AUTHORIZATION, "Bearer " + session.getToken().getAccessToken());
        }
        Map<String, String> headers = session.getHeaders();
        for (String key : headers.keySet()) {
            client.header(key, headers.get(key));
        }
    }

    //////////////////////////////////////////////////////////

    public LoggingInInterceptor getInLogger() {
        return inLogger;
    }

    public void setInLogger(LoggingInInterceptor inLogger) {
        this.inLogger = inLogger;
    }

    public LoggingOutInterceptor getOutLogger() {
        return outLogger;
    }

    public void setOutLogger(LoggingOutInterceptor outLogger) {
        this.outLogger = outLogger;
    }

    public boolean isLogExchange() {
        return logExchange;
    }

    public void setLogExchange(boolean logExchange) {
        this.logExchange = logExchange;
    }

    public List<Object> getProviders() {
        return providers;
    }

    public JacksonJsonProvider getJacksonJsonProvider() {
        return jacksonJsonProvider;
    }

}
