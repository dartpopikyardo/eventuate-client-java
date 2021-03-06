package io.eventuate.javaclient.spring.jdbc;

import io.eventuate.javaclient.commonimpl.AggregateCrud;
import io.eventuate.javaclient.commonimpl.AggregateEvents;
import io.eventuate.javaclient.commonimpl.adapters.SyncToAsyncAggregateCrudAdapter;
import io.eventuate.javaclient.commonimpl.adapters.SyncToAsyncAggregateEventsAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
public class EmbeddedTestAggregateStoreConfiguration {

  @Bean
  public EventuateEmbeddedTestAggregateStore eventuateEmbeddedTestAggregateStore() {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
    return new EventuateEmbeddedTestAggregateStore(jdbcTemplate);
  }

  @Bean
  public DataSource dataSource() {
    EmbeddedDatabaseBuilder builder = new EmbeddedDatabaseBuilder();
    return builder.setType(EmbeddedDatabaseType.H2).addScript("embedded-event-store-schema.sql").build();
  }

  @Bean
  public AggregateCrud aggregateCrud(io.eventuate.javaclient.commonimpl.sync.AggregateCrud aggregateCrud) {
    return new SyncToAsyncAggregateCrudAdapter(aggregateCrud);
  }

  @Bean
  public AggregateEvents aggregateEvents(io.eventuate.javaclient.commonimpl.sync.AggregateEvents aggregateEvents) {
    return new SyncToAsyncAggregateEventsAdapter(aggregateEvents);
  }


  @Bean
  public io.eventuate.EventuateAggregateStore eventuateAggregateStore(AggregateCrud aggregateCrud, AggregateEvents aggregateEvents) {
    return new io.eventuate.javaclient.commonimpl.EventuateAggregateStoreImpl(aggregateCrud, aggregateEvents);
  }

  @Bean
  public io.eventuate.sync.EventuateAggregateStore syncEventuateAggregateStore(io.eventuate.javaclient.commonimpl.sync.AggregateCrud aggregateCrud,
                                                                               io.eventuate.javaclient.commonimpl.sync.AggregateEvents aggregateEvents) {
    return new io.eventuate.javaclient.commonimpl.sync.EventuateAggregateStoreImpl(aggregateCrud, aggregateEvents);
  }

}
