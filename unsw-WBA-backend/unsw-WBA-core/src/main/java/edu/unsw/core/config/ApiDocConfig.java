package edu.unsw.core.config;

import edu.unsw.core.constant.RequestPrefixConstant;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/6/17 19:39
 * @description OpenApi configuration
 */
@SpringBootConfiguration
public class ApiDocConfig {
    @Bean
    public OpenAPI docsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("UNSW WBA API Doc")
                        .description("Online automatically generated documents")
                        .version("1.0.0")
                        .contact(new Contact().url("https://github.com/unsw-cse-comp99-3900/unsw-comp9900-WBA-backend.git").name("W13C ALMOND").email("yijie.xu3@student.unsw.edu.au"))
                        .license(new License().name("Apache Software License V2.0").url("https://www.apache.org/licenses/LICENSE-2.0.txt"))
                );
    }

    @Bean
    public GroupedOpenApi generalApi() {
        return GroupedOpenApi.builder()
                .group("General API")
                .pathsToMatch(RequestPrefixConstant.GENERAL_PREFIX + "/**")
                .build();
    }

    @Bean
    public GroupedOpenApi studentApi() {
        return GroupedOpenApi.builder()
                .group("Student API")
                .pathsToMatch(RequestPrefixConstant.STUDENT_PREFIX + "/**")
                .build();
    }

    @Bean
    public GroupedOpenApi supervisorApi() {
        return GroupedOpenApi.builder()
                .group("Supervisor API")
                .pathsToMatch(RequestPrefixConstant.SUPERVISOR_PREFIX + "/**")
                .build();
    }

    @Bean
    public GroupedOpenApi registrarApi() {
        return GroupedOpenApi.builder()
                .group("Registrar API")
                .pathsToMatch(RequestPrefixConstant.REGISTRAR_PREFIX + "/**")
                .build();
    }

}
