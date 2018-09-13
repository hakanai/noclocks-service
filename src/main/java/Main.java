import ratpack.server.BaseDir;
import ratpack.server.RatpackServer;
import ratpack.groovy.template.TextTemplateModule;
import ratpack.guice.Guice;

import static ratpack.groovy.Groovy.groovyTemplate;

public class Main {
  public static void main(String... args) throws Exception {
    RatpackServer.start(server ->
      server
        .serverConfig(c ->
          config
            .baseDir(BaseDir.find())
            .env())

        .registry(Guice.registry(b -> {
          //b.module(TextTemplateModule.class, conf -> conf.setStaticallyCompile(true));
        }))

        .handlers(chain -> chain
          .get("/api/1/utc", context -> {
            context.render("Hello!");
            //TODO: make the rest of the fucking clock;
          })
        ));
  }
}