/**
 * Copyright (c) 2015-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.ui.habot.rest.internal;

import java.util.Locale;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.smarthome.core.auth.Role;
import org.eclipse.smarthome.core.i18n.LocaleProvider;
import org.eclipse.smarthome.core.voice.VoiceManager;
import org.eclipse.smarthome.io.rest.LocaleUtil;
import org.eclipse.smarthome.io.rest.RESTResource;
import org.openhab.ui.habot.nlp.ChatReply;
import org.openhab.ui.habot.nlp.internal.AnswerFormatter;
import org.openhab.ui.habot.nlp.internal.OpenNLPInterpreter;
import org.openhab.ui.habot.notification.internal.NotificationService;
import org.openhab.ui.habot.notification.internal.webpush.Subscription;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * This class describes the /habot resource of the REST API.
 *
 * @author Yannick Schaus - Initial contribution
 *
 */
@Path(HABotResource.PATH_HABOT)
@Api(HABotResource.PATH_HABOT)
@Component(service = RESTResource.class, immediate = true)
public class HABotResource implements RESTResource {

    private final Logger logger = LoggerFactory.getLogger(HABotResource.class);

    private final String OPENNLP_HLI = "opennlp";

    private VoiceManager voiceManager;

    private LocaleProvider localeProvider;

    private NotificationService notificationService;

    @Reference
    public void setVoiceManager(VoiceManager voiceManager) {
        this.voiceManager = voiceManager;
    }

    public void unsetVoiceManager(VoiceManager voiceManager) {
        this.voiceManager = null;
    }

    @Reference
    public void setLocaleProvider(LocaleProvider localeProvider) {
        this.localeProvider = localeProvider;
    }

    public void unsetLocaleProvider(LocaleProvider localeProvider) {
        this.localeProvider = null;
    }

    @Reference
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void unsetNotificationService(NotificationService notificationService) {
        this.notificationService = null;
    }

    public static final String PATH_HABOT = "habot";

    @GET
    @RolesAllowed({ Role.USER, Role.ADMIN })
    @Path("/greet")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Retrieves a first greeting message from the bot in the specified or configured language.")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK", response = ChatReply.class),
            @ApiResponse(code = 500, message = "There is no support for the configured language") })
    public Response greet(
            @HeaderParam(HttpHeaders.ACCEPT_LANGUAGE) @ApiParam(value = "language (will use the default if omitted)") String language) {

        final Locale locale = (this.localeProvider != null && this.localeProvider.getLocale() != null)
                ? this.localeProvider.getLocale()
                : LocaleUtil.getLocale(language);

        AnswerFormatter answerFormatter = new AnswerFormatter(locale);

        String greeting = answerFormatter.getRandomAnswer("greeting");
        ChatReply reply = new ChatReply(locale);
        reply.setAnswer(greeting);

        return Response.ok(reply).build();
    }

    @POST
    @RolesAllowed({ Role.USER, Role.ADMIN })
    @Path("/chat")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Send a query to HABot to interpret.")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK", response = ChatReply.class),
            @ApiResponse(code = 500, message = "An interpretation error occured") })
    public Response chat(@HeaderParam(HttpHeaders.ACCEPT_LANGUAGE) @ApiParam(value = "language") String language,
            @ApiParam(value = "human language query", required = true) String query) throws Exception {

        final Locale locale = (this.localeProvider != null && this.localeProvider.getLocale() != null)
                ? this.localeProvider.getLocale()
                : LocaleUtil.getLocale(language);

        // interpret
        OpenNLPInterpreter hli = (OpenNLPInterpreter) voiceManager.getHLI(OPENNLP_HLI);
        ChatReply reply = hli.reply(locale, query);

        return Response.ok(reply).build();
    }

    @POST
    @Path("/notifications/subscribe")
    // Can't POST JSON data with openHAB Cloud since Jan 2017
    // (https://github.com/openhab/openhab-cloud/issues/31)
    // so have to work around that...
    @Consumes(MediaType.TEXT_PLAIN) // @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Subscribes a new client for push notifications")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 500, message = "An error occured") })
    public Response webPushSubscribe(String subscriptionJson) throws Exception {
        Gson gson = new Gson();
        Subscription subscription = gson.fromJson(subscriptionJson, Subscription.class);
        notificationService.addSubscription(subscription);

        // send a test notification to the client
        notificationService.sendNotification(subscription,
                "{ \"body\": \"Congratulations, push notifications are working properly! 🎉👌\" }");

        return Response.ok().build();
    }

    @GET
    @Path("/notifications/vapid")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Gets or generates the public VAPID key used for push notifications.")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 500, message = "An error occured") })
    public Response webPushConfig() throws Exception {
        String publicVAPIDKey = notificationService.getVAPIDPublicKey();

        return Response.ok(publicVAPIDKey).build();
    }
}
