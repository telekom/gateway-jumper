package jumper.config;

import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.REACTIVE;

import java.util.stream.Collectors;
import jumper.exception.JsonErrorWebExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.result.view.ViewResolver;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = REACTIVE)
@ConditionalOnClass(WebFluxConfigurer.class)
@EnableConfigurationProperties({ServerProperties.class, WebProperties.class})
public class CustomErrorWebFluxAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(value = ErrorWebExceptionHandler.class, search = SearchStrategy.CURRENT)
  @Order(-1)
  public ErrorWebExceptionHandler errorWebExceptionHandler(
      ErrorAttributes errorAttributes,
      WebProperties webProperties,
      ObjectProvider<ViewResolver> viewResolvers,
      ServerCodecConfigurer serverCodecConfigurer,
      ApplicationContext applicationContext,
      ServerProperties serverProperties,
      Tracer tracer,
      CurrentTraceContext currentTraceContext) {

    JsonErrorWebExceptionHandler exceptionHandler =
        new JsonErrorWebExceptionHandler(
            errorAttributes,
            webProperties.getResources(),
            serverProperties.getError(),
            applicationContext,
            tracer,
            currentTraceContext);

    exceptionHandler.setViewResolvers(viewResolvers.orderedStream().collect(Collectors.toList()));
    exceptionHandler.setMessageWriters(serverCodecConfigurer.getWriters());
    exceptionHandler.setMessageReaders(serverCodecConfigurer.getReaders());

    return exceptionHandler;
  }

  @Bean
  @ConditionalOnMissingBean(value = ErrorAttributes.class, search = SearchStrategy.CURRENT)
  public DefaultErrorAttributes errorAttributes() {
    return new DefaultErrorAttributes();
  }
}
