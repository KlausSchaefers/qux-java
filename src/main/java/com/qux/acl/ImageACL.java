package com.qux.acl;

import com.qux.model.App;
import com.qux.model.Team;
import com.qux.model.User;
import com.qux.util.DB;
import io.vertx.core.Handler;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageACL extends MongoAcl implements Acl{

    private final String app_db, team_db;

    private final Logger logger = LoggerFactory.getLogger(ImageACL.class);

    public ImageACL(MongoClient client){
        this(client, "appID");
    }


    public ImageACL(MongoClient client, String id){
        super(client, id);
        this.app_db = DB.getTable(App.class);
        this.team_db = DB.getTable(Team.class);
    }


    @Override
    public void canCreate(User user, RoutingContext event, Handler<Boolean> handler) {
        if(user.hasRole(User.USER) || user.hasRole(User.ADMIN)){
            handler.handle(true);
        } else {
            handler.handle(false);
        }
    }

    @Override
    public void canRead(User user, RoutingContext event, Handler<Boolean> handler) {
        String appID = getId(event);
        logger.debug("canRead() > {}", appID);

        /*
         * First check if there is an Team entry, otherwise check if app is public
         */
        client.count(team_db, Team.canRead(user, appID), res-> {
            /*
             * FIXME: This is an ugly workaround. Sometimes we have double entries in the team list...
             */
            if(res.succeeded() && res.result() >= 1l){
                if (res.result() > 1) {
                    logger.error("canRead() > count bigger 1");
                }
                handler.handle(true);
            } else {
                logger.error("canRead() > Check Public because > success: {} >> count:{}", res.succeeded(), res.result());
                client.count(app_db, App.findPublicByID(appID), isPublic ->{
                    assertOne(isPublic, handler, event, user);
                });
            }
        });
    }

    @Override
    public void canWrite(User user, RoutingContext event, Handler<Boolean> handler) {
        String id = getId(event);
        client.count(team_db, Team.canWrite(user, id), res->{
            assertOne(res, handler, event, user);
        });
    }

    @Override
    public void canDelete(User user, RoutingContext event, Handler<Boolean> handler) {
        String id = getId(event);
        client.count(team_db, Team.canWrite(user, id),  res->{
            assertOne(res, handler, event, user);
        });
    }




}
