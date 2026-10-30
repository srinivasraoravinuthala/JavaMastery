# 52 — Apache Kafka Client in Java

**Previous:** [51 gRPC in Java](51-gRPCInJava.md) · **Next:** [53 OpenTelemetry in Java](53-OpenTelemetry.md)

---

## Kafka Core Concepts

```
Producer  → Topic → Partitions → Consumer Group
                         |
                    Offset (position in partition)
```

| Concept | Description |
|---------|------------|
| **Topic** | Category of messages (like a table in a DB) |
| **Partition** | Ordered, immutable log within a topic; unit of parallelism |
| **Offset** | Position of a message in a partition; committed by consumer |
| **Consumer Group** | Load-balances partitions across consumers; one consumer per partition max |
| **Broker** | Kafka server; a cluster has multiple brokers |
| **Retention** | Messages kept for N days (default 7); consumers read at any pace |

---

## 1. Maven Dependencies

```xml
<!-- Kafka client (no Spring required) -->
<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-clients</artifactId>
    <version>3.7.0</version>
</dependency>

<!-- Spring Kafka (Spring Boot integration) -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

---

## 2. Pure Kafka Client

### Producer

```java
import org.apache.kafka.clients.producer.*;
import java.util.Properties;

Properties props = new Properties();
props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
    "org.apache.kafka.common.serialization.StringSerializer");
props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
    "org.apache.kafka.common.serialization.StringSerializer");

// Reliability settings
props.put(ProducerConfig.ACKS_CONFIG, "all");            // wait for all replicas
props.put(ProducerConfig.RETRIES_CONFIG, 3);
props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // exactly-once

try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
    for (int i = 0; i < 10; i++) {
        ProducerRecord<String, String> record =
            new ProducerRecord<>("orders", "order-" + i, "{\"amount\": " + i * 100 + "}");

        // Async send with callback
        producer.send(record, (metadata, ex) -> {
            if (ex != null) {
                System.err.println("Send failed: " + ex.getMessage());
            } else {
                System.out.printf("Sent to %s partition=%d offset=%d%n",
                    metadata.topic(), metadata.partition(), metadata.offset());
            }
        });
    }
    producer.flush();  // wait for all in-flight sends
}
```

### Consumer

```java
import org.apache.kafka.clients.consumer.*;

Properties props = new Properties();
props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
props.put(ConsumerConfig.GROUP_ID_CONFIG, "inventory-service");
props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
    "org.apache.kafka.common.serialization.StringDeserializer");
props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
    "org.apache.kafka.common.serialization.StringDeserializer");
props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");  // start from beginning if no offset
props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);       // manual commit for exactly-once

try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
    consumer.subscribe(List.of("orders"));

    while (true) {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));

        for (ConsumerRecord<String, String> record : records) {
            System.out.printf("key=%s value=%s partition=%d offset=%d%n",
                record.key(), record.value(), record.partition(), record.offset());
            processOrder(record.value());
        }

        // Manual commit AFTER processing — at-least-once guarantee
        consumer.commitSync();
    }
}
```

---

## 3. Spring Kafka

### Configuration

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      retries: 3
      properties:
        enable.idempotence: true
    consumer:
      group-id: inventory-service
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      auto-offset-reset: earliest
      properties:
        spring.json.trusted.packages: "*"
```

### Producer

```java
@Service
public class OrderProducer {
    @Autowired
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public void publishOrder(Order order) {
        OrderEvent event = new OrderEvent(order.getId(), "PLACED", order);
        kafkaTemplate.send("orders", order.getId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish order {}", order.getId(), ex);
                } else {
                    log.info("Published order {} to partition {}",
                        order.getId(), result.getRecordMetadata().partition());
                }
            });
    }
}
```

### Consumer

```java
@Component
public class OrderConsumer {

    @KafkaListener(
        topics = "orders",
        groupId = "inventory-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(OrderEvent event, Acknowledgment ack) {
        try {
            inventoryService.processOrder(event);
            ack.acknowledge();  // manual ack — commit offset
        } catch (RetryableException e) {
            // Don't ack — will be retried
            log.warn("Retryable error for order {}", event.orderId(), e);
        }
    }

    // Batch consumption
    @KafkaListener(topics = "audit", batch = "true")
    public void consumeBatch(List<AuditEvent> events) {
        auditRepository.saveAll(events);
    }
}
```

### Dead Letter Queue (DLQ)

```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, String> factory(
        ConsumerFactory<String, String> cf,
        KafkaTemplate<String, String> template) {

    var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
    factory.setConsumerFactory(cf);

    // Retry 3 times, then send to DLQ
    var recoverer = new DeadLetterPublishingRecoverer(template,
        (rec, ex) -> new TopicPartition(rec.topic() + ".DLT", rec.partition()));

    factory.setCommonErrorHandler(
        new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3)));

    return factory;
}
```

---

## 4. Delivery Guarantees

| Guarantee | Producer Config | Consumer Config |
|-----------|----------------|-----------------|
| **At-most-once** | acks=0 | auto-commit before processing |
| **At-least-once** | acks=all, retries>0 | manual commit after processing |
| **Exactly-once** | `enable.idempotence=true` + `isolation.level=read_committed` | transactional consumer |

### Exactly-Once (Transactional)

```java
// Producer
props.put("transactional.id", "order-producer-1");  // unique per producer instance

producer.initTransactions();
try {
    producer.beginTransaction();
    producer.send(new ProducerRecord<>("orders", key, value));
    producer.send(new ProducerRecord<>("audit", key, auditValue));
    producer.commitTransaction();
} catch (Exception e) {
    producer.abortTransaction();
    throw e;
}
```

---

## 5. Key Design Patterns

### Partitioning Strategy
```java
// Custom partitioner — route orders by customer region
public class RegionPartitioner implements Partitioner {
    @Override
    public int partition(String topic, Object key, byte[] keyBytes,
                         Object value, byte[] valueBytes, Cluster cluster) {
        String region = ((Order) value).getRegion();
        int numPartitions = cluster.partitionCountForTopic(topic);
        return switch (region) {
            case "US" -> 0;
            case "EU" -> 1 % numPartitions;
            default   -> Math.abs(region.hashCode() % numPartitions);
        };
    }
}
```

### Consumer Group Scaling
```
Topic: orders (4 partitions)

One consumer group, 2 consumers:
  Consumer A → Partition 0, Partition 1
  Consumer B → Partition 2, Partition 3

One consumer group, 4 consumers:
  Consumer A → Partition 0
  Consumer B → Partition 1
  Consumer C → Partition 2
  Consumer D → Partition 3

5 consumers → one idles (max parallelism = partition count)
```

### Outbox Pattern (Guaranteed Delivery)

```java
@Transactional
public void createOrder(Order order) {
    orderRepository.save(order);
    // Write event to outbox table in same transaction
    outboxRepository.save(new OutboxMessage("ORDER_PLACED", toJson(order)));
}

// Separate poller thread — publish outbox messages to Kafka
@Scheduled(fixedDelay = 1000)
public void processOutbox() {
    outboxRepository.findPendingMessages(100).forEach(msg -> {
        kafkaTemplate.send("orders", msg.getAggregateId(), msg.getPayload());
        outboxRepository.markPublished(msg.getId());
    });
}
```

---

## Interview Questions

1. What happens if you add more consumers than partitions? → Extra consumers sit idle — maximum parallelism equals partition count.
2. How does Kafka guarantee ordering? → Within a partition — messages are strictly ordered. Across partitions — no ordering guarantee. Use a single partition or partition by key to ensure ordered delivery per entity.
3. At-least-once vs exactly-once? → At-least-once: commit offset after processing; on failure, reprocess (duplicates possible). Exactly-once: idempotent producer + transactional consumer ensures each message processed exactly once.
4. What is consumer lag? → How far behind a consumer group is from the latest offset. Monitor with `kafka-consumer-groups.sh --describe`. High lag = consumer is too slow or has crashed.
5. When to use Kafka vs a database queue? → Kafka for high-throughput event streaming, fan-out (multiple consumer groups), replay, and long retention. DB queue (e.g., Outbox) for small-scale, transactional, single-consumer workflows.

---

**Next →** [53 OpenTelemetry in Java](53-OpenTelemetry.md)
