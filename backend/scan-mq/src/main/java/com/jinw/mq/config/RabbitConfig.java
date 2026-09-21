package com.jinw.mq.config;

import com.jinw.common.constant.ScanConstant;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RabbitConfig {

    @Value("${spring.rabbitmq.host}")
    private String host;

    @Value("${spring.rabbitmq.port}")
    private int port;

    @Value("${spring.rabbitmq.username}")
    private String username;

    @Value("${spring.rabbitmq.password}")
    private String password;

    @Value("${spring.rabbitmq.virtual-host}")
    private String virtualHost;

    @Bean
    @Primary
    public ConnectionFactory connectionFactory() {
        com.rabbitmq.client.ConnectionFactory clientFactory = new com.rabbitmq.client.ConnectionFactory();
        clientFactory.setHost(host);
        clientFactory.setPort(port);
        clientFactory.setUsername(username);
        clientFactory.setPassword(password);
        clientFactory.setVirtualHost(virtualHost);
        clientFactory.setMaxInboundMessageBodySize(256 * 1024 * 1024);

        return new CachingConnectionFactory(clientFactory);
    }

    /**
     * 文件扫描任务队列
     */
    @Bean
    public Queue scanFileTaskQueue() {
        return new Queue(ScanConstant.SCAN_FILE_TASK_QUEUE, true);
    }

    /**
     * 文件扫描结果队列
     */
    @Bean
    public Queue scanFileResultQueue() {
        return new Queue(ScanConstant.SCAN_FILE_RESULT_QUEUE, true);
    }

    /**
     * 文件开始扫描队列
     */
    @Bean
    public Queue scanFileStartQueue() {
        return new Queue(ScanConstant.SCAN_FILE_START_QUEUE, true);
    }

    /**
     * 大模型适配任务队列
     */
    @Bean
    public Queue llmFileTaskQueue() {
        return new Queue(ScanConstant.LLM_FILE_TASK_QUEUE, true);
    }

    /**
     * 大模型适配结果队列
     */
    @Bean
    public Queue llmFileResultQueue() {
        return new Queue(ScanConstant.LLM_FILE_RESULT_QUEUE, true);
    }

    /**
     * 文件扫描任务队列
     */
    @Bean
    public Queue verifyFileTaskQueue() {
        return new Queue(ScanConstant.VERIFY_FILE_TASK_QUEUE, true);
    }

    /**
     * 文件扫描结果队列
     */
    @Bean
    public Queue verifyFileResultQueue() {
        return new Queue(ScanConstant.VERIFY_FILE_RESULT_QUEUE, true);
    }

    /**
     * 文件开始扫描队列
     */
    @Bean
    public Queue verifyFileStartQueue() {
        return new Queue(ScanConstant.VERIFY_FILE_START_QUEUE, true);
    }

    /**
     * 释放任务队列
     */
    @Bean
    public Queue abortTaskQueue() {
        return new Queue(ScanConstant.ABORT_TASK_QUEUE, true);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf) {

        RabbitTemplate template = new RabbitTemplate(cf);

        template.setMessageConverter(messageConverter());

        return template;
    }

    @Bean(name = "llmListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory llmListenerContainerFactory(
            ConnectionFactory connectionFactory
    ) {
        SimpleRabbitListenerContainerFactory factory =
                new SimpleRabbitListenerContainerFactory();

        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());

        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setDefaultRequeueRejected(false);

        return factory;
    }
}