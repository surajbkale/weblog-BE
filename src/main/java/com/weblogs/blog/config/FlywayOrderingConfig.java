package com.weblogs.blog.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Spring Boot 4.x does not reliably guarantee that Flyway runs before Hibernate's
 * schema validation. This post-processor explicitly declares that 'entityManagerFactory'
 * depends on both 'flyway' and 'flywayInitializer' beans, forcing the correct order:
 *
 *   Flyway migrations → Hibernate validate → app starts
 */
@Configuration
public class FlywayOrderingConfig implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // Try both names — Spring Boot may use either depending on version/config
        addDependsOn(beanFactory, "entityManagerFactory", "flyway");
        addDependsOn(beanFactory, "entityManagerFactory", "flywayInitializer");
    }

    private void addDependsOn(ConfigurableListableBeanFactory bf, String bean, String dependency) {
        if (!bf.containsBeanDefinition(bean) || !bf.containsBeanDefinition(dependency)) {
            return;
        }
        BeanDefinition def = bf.getBeanDefinition(bean);
        String[] current = def.getDependsOn();
        List<String> deps = current != null
                ? new ArrayList<>(Arrays.asList(current))
                : new ArrayList<>();
        if (!deps.contains(dependency)) {
            deps.add(dependency);
            def.setDependsOn(deps.toArray(new String[0]));
        }
    }
}
