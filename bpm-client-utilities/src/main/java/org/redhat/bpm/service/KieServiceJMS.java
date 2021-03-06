package org.redhat.bpm.service;

import org.kie.server.api.marshalling.MarshallingFormat;
import org.kie.server.client.KieServicesFactory;
import org.kie.server.client.jms.RequestReplyResponseHandler;
import org.redhat.bpm.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.ConnectionFactory;
import javax.jms.Queue;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.Properties;


public class KieServiceJMS extends KieService {

    private static final Logger LOG = LoggerFactory.getLogger(KieServiceJMS.class);

    public KieServiceJMS(GatewaySettings settings) {

        final Properties env = new Properties();
        env.put(Context.INITIAL_CONTEXT_FACTORY, Constants.INITIAL_CONTEXT_FACTORY);
        env.put(Context.PROVIDER_URL, Constants.REMOTING_URL);
        env.put(Context.SECURITY_PRINCIPAL, settings.getUsername());
        env.put(Context.SECURITY_CREDENTIALS, settings.getPassword());

        try {

            InitialContext context = new InitialContext(env);

            Queue requestQueue = (Queue) context.lookup(Constants.REQUEST_QUEUE_JNDI);
            Queue responseQueue = (Queue) context.lookup(Constants.RESPONSE_QUEUE_JNDI);
            ConnectionFactory connectionFactory = (ConnectionFactory) context.lookup(Constants.CONNECTION_FACTORY);

            config = KieServicesFactory.newJMSConfiguration(connectionFactory, requestQueue, responseQueue, settings.getUsername(), settings.getPassword());
            // Set your response handler globally here.
            config.setResponseHandler(new RequestReplyResponseHandler());
            config.setMarshallingFormat(MarshallingFormat.JAXB);

        } catch(Exception e) {
            final String errorMessage = "Can't establish a JMS KIE conf";
            LOG.error(errorMessage, e);
            throw new IllegalStateException(errorMessage);
        }

    }

}
