package com.sie.iidp.demo.service;

import com.alibaba.druid.support.http.StatViewServlet;
import com.alibaba.druid.support.http.WebStatFilter;
import com.sie.snest.engine.container.AppLifecycleProcess;
import com.sie.snest.engine.container.Engine;
import com.sie.snest.engine.container.load.WebMode;
import com.sie.snest.engine.container.load.factory.LoaderFactory;
import com.sie.snest.engine.container.manger.IMetaManager;
import com.sie.snest.engine.container.manger.IMetaType;
import com.sie.snest.engine.container.manger.factory.IMetaFactory;
import com.sie.snest.engine.model.Loader;
import com.sie.snest.engine.utils.ConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import javax.servlet.Servlet;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * IIDP Demo工程启动类
 * @author zhoubin
 * @date 2024年03月20日 19:10
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@ComponentScan(basePackages = "com.sie.snest")
public class DemoServer {
    private static final Logger logger = LoggerFactory.getLogger(DemoServer.class);

    public static void main(String[] args) {
        ConfigUtils.loadAllConfig();

        String metaStoreType = ConfigUtils.get("engine.store.meta.mode");
        String runMode = ConfigUtils.get("engine.run.mode");

        AppLifecycleProcess loader = LoaderFactory.getAppLoader(WebMode.of(runMode));
        IMetaManager metaManager = IMetaFactory.getMetaStoreManager(IMetaType.fromString(metaStoreType));
        loader.setMetaManager(metaManager);
        // 启动引擎
        Loader.setLoader(loader);
        logger.info("Loader: {}, IMetaManager: {}", loader.getClass().getName(), metaManager.getClass().getName());

        Engine.start();

        // 启动SpringBoot
        SpringApplication.run(DemoServer.class, args);

    }

    @Bean
    public ServletRegistrationBean druidServletRegistrationBean() {
        ServletRegistrationBean<Servlet> servletRegistrationBean = new ServletRegistrationBean<>(new StatViewServlet(), "/druid/*");
        Map<String, String> initParams = new HashMap<>();
        initParams.put("loginUsername", "admin");
        initParams.put("loginPassword", "123456");
        initParams.put("allow", "");
        servletRegistrationBean.setInitParameters(initParams);
        return servletRegistrationBean;
    }

    // 配置 Druid 监控 之 web 监控的 filter
    // WebStatFilter：用于配置Web和Druid数据源之间的管理关联监控统计
    @Bean
    public FilterRegistrationBean webStatFilter() {
        FilterRegistrationBean bean = new FilterRegistrationBean();
        bean.setFilter(new WebStatFilter());

        // exclusions：设置哪些请求进行过滤排除掉，从而不进行统计
        Map<String, String> initParams = new HashMap<>();
        initParams.put("exclusions", "*.js,*.css,/druid/*");
        bean.setInitParameters(initParams);

        // "/*" 表示过滤所有请求
        bean.setUrlPatterns(Arrays.asList("/*"));
        return bean;
    }
}