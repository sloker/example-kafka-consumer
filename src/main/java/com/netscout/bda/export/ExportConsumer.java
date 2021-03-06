package com.netscout.bda.export;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Example consumer to use for reference.  The consumer supports both secured & non-secured Kafka clusters and can consume
 * from one or more Kafka topics.  This is a simple example consumer and no emphasis was put on efficiency.
 */
public class ExportConsumer {

    private static final Logger logger = LoggerFactory.getLogger(ExportConsumer.class);

    private String bootstrapServers;
    private String consumerGroup;
    private String kafkaServiceName;
    private String kafkaClientId;
    private String trustPassword;
    private String trustFileLocation;
    private List<String> topics = new ArrayList<String>();

    private String basePath;
    private int maxRecords;
    private Map<String, CsvWriter> writers = new HashMap<String, CsvWriter>();

    /**
     * Constructs an ExportConsumer for a specific set of kafka bootstrap server, consumer group and list of topics
     *
     * @param bootstrapServers Kafka bootstrap servers to connect to
     * @param consumerGroup    Kafka consumer group to use
     * @param topics           Kafka topics to consume from
     * @param basePath         full path to the directory for CSVs to be written to
     * @param maxRecords       max number of records written to each CSV file
     */
    public ExportConsumer(final String bootstrapServers, final String consumerGroup, final List topics, final String basePath, final int maxRecords) {
        this.bootstrapServers = bootstrapServers;
        this.consumerGroup = consumerGroup;
        this.trustPassword = "";
        this.topics = topics;
        this.basePath = basePath;
        this.maxRecords = maxRecords;
    }

    /**
     * Enables SASL_SSL security for the consumer
     *
     * @param kafkaServiceName  Kerberos service name used by the kafka cluster
     * @param kafkaClientId     Kafka client id
     * @param trustFileLocation Location of SSL truststore file
     * @param trustPassword     Password for SSL truststore file
     */
    public void withSecurity(final String kafkaServiceName, final String kafkaClientId, final String trustFileLocation, final String trustPassword) {
        this.kafkaServiceName = kafkaServiceName;
        this.kafkaClientId = kafkaClientId;
        this.trustFileLocation = trustFileLocation;
        this.trustPassword = trustPassword;
    }

    /**
     * Starts the consumer and will run continuously polling the configured topics
     */
    public void start() {
        KafkaConsumer<String, Map<String, Object>> consumer = new KafkaConsumer<String, Map<String, Object>>(getConsumerProperties());
        consumer.subscribe(topics);

        while (true) {
            ConsumerRecords<String, Map<String, Object>> records = consumer.poll(10000);
            for (ConsumerRecord<String, Map<String, Object>> rec : records) {
                if (!writers.containsKey(rec.topic())) {
                    writers.put(rec.topic(), new CsvWriter(rec.topic(), basePath, maxRecords));
                }

                writers.get(rec.topic()).write(rec.value());
            }
        }
    }

    /**
     * List the topics the user has permission to see on the Kafka broker
     */
    public void listTopics() {
        KafkaConsumer<String, Map<String, Object>> consumer = new KafkaConsumer<String, Map<String, Object>>(getConsumerProperties());
        Map<String, List<PartitionInfo>> topics = consumer.listTopics();

        logger.info("Topics: ");
        for (String topic : topics.keySet()) {
            logger.info("\t" + topic);
        }
    }

    /**
     * Builds the appropriate Kafka consumer properties
     *
     * @return Properties containing appropriate settings for the Kafka consumer
     */
    private Properties getConsumerProperties() {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("group.id", consumerGroup);
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", "1000");
        props.put("session.timeout.ms", "30000");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "com.netscout.bda.export.JsonDeserializer");

        if (isNotBlank(trustPassword)) {
            addSecurity(props);
        }

        return props;
    }

    /**
     * Add SASL_SSL security settings to the input properties
     *
     * @param props Properties to add security parameters to
     */
    private void addSecurity(Properties props) {
        props.setProperty("security.protocol", "SASL_SSL");
        props.setProperty("sasl.mechanism", "GSSAPI");
        props.setProperty("sasl.kerberos.service.name", kafkaServiceName);
        props.setProperty("client.id", kafkaClientId);
        props.setProperty("ssl.truststore.location", trustFileLocation);
        props.setProperty("ssl.truststore.password", trustPassword);
    }
}
