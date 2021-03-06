package io.eventuate.javaclient.commonimpl.sync;

import io.eventuate.Aggregate;
import io.eventuate.Aggregates;
import io.eventuate.CompletableFutureUtil;
import io.eventuate.DispatchedEvent;
import io.eventuate.EntityIdAndType;
import io.eventuate.EntityIdAndVersion;
import io.eventuate.EntityWithMetadata;
import io.eventuate.Event;
import io.eventuate.FindOptions;
import io.eventuate.Int128;
import io.eventuate.SaveOptions;
import io.eventuate.SubscriberOptions;
import io.eventuate.UpdateOptions;
import io.eventuate.javaclient.commonimpl.DefaultSerializedEventDeserializer;
import io.eventuate.javaclient.commonimpl.EntityIdVersionAndEventIds;
import io.eventuate.javaclient.commonimpl.EventIdTypeAndData;
import io.eventuate.javaclient.commonimpl.EventTypeAndData;
import io.eventuate.javaclient.commonimpl.JSonMapper;
import io.eventuate.javaclient.commonimpl.LoadedEvents;
import io.eventuate.javaclient.commonimpl.SerializedEventDeserializer;
import io.eventuate.sync.EventuateAggregateStore;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.eventuate.javaclient.commonimpl.EventuateActivity.activityLogger;

public class EventuateAggregateStoreImpl implements EventuateAggregateStore {

  private AggregateCrud aggregateCrud;
  private AggregateEvents aggregateEvents;
  private SerializedEventDeserializer serializedEventDeserializer = new DefaultSerializedEventDeserializer();

  public EventuateAggregateStoreImpl(AggregateCrud aggregateCrud, AggregateEvents aggregateEvents) {
    this.aggregateCrud = aggregateCrud;
    this.aggregateEvents = aggregateEvents;
  }

  public void setSerializedEventDeserializer(SerializedEventDeserializer serializedEventDeserializer) {
    this.serializedEventDeserializer = serializedEventDeserializer;
  }

  @Override
  public <T extends Aggregate<T>> EntityIdAndVersion save(Class<T> clasz, List<Event> events) {
    return save(clasz, events, Optional.empty());
  }

  @Override
  public <T extends Aggregate<T>> EntityIdAndVersion save(Class<T> clasz, List<Event> events, SaveOptions saveOptions) {
    return save(clasz, events, Optional.ofNullable(saveOptions));
  }

  @Override
  public <T extends Aggregate<T>> EntityIdAndVersion save(Class<T> clasz, List<Event> events, Optional<SaveOptions> saveOptions) {
    List<EventTypeAndData> serializedEvents = events.stream().map(this::toEventTypeAndData).collect(Collectors.toList());
    try {
      EntityIdVersionAndEventIds result = aggregateCrud.save(clasz.getName(), serializedEvents, saveOptions);
      if (activityLogger.isDebugEnabled())
        activityLogger.debug("Saved entity: {} {} {}", clasz.getName(), result.getEntityId(), toSerializedEventsWithIds(serializedEvents, result.getEventIds()));
      return result.toEntityIdAndVersion();
    } catch (RuntimeException e) {
      activityLogger.error(String.format("Save entity failed: %s", clasz.getName()), e);
      throw e;
    }
  }

  private List<EventIdTypeAndData> toSerializedEventsWithIds(List<EventTypeAndData> serializedEvents, List<Int128> eventIds) {
    return IntStream.range(0, serializedEvents.size()).boxed().map(idx ->
            new EventIdTypeAndData(eventIds.get(idx),
                    serializedEvents.get(idx).getEventType(),
                    serializedEvents.get(idx).getEventData())).collect(Collectors.toList());
  }


  @Override
  public <T extends Aggregate<T>> EntityWithMetadata<T> find(Class<T> clasz, String entityId) {
    return find(clasz, entityId, Optional.empty());
  }

  @Override
  public <T extends Aggregate<T>> EntityWithMetadata<T> find(Class<T> clasz, String entityId, FindOptions findOptions) {
    return find(clasz, entityId, Optional.ofNullable(findOptions));
  }

  private EventTypeAndData toEventTypeAndData(Event event) {
    return new EventTypeAndData(event.getClass().getName(), JSonMapper.toJson(event));
  }

  @Override
  public <T extends Aggregate<T>> EntityWithMetadata<T> find(Class<T> clasz, String entityId, Optional<FindOptions> findOptions) {
    try {
      LoadedEvents le = aggregateCrud.find(clasz.getName(), entityId, findOptions);
      if (activityLogger.isDebugEnabled())
        activityLogger.debug("Loaded entity: {} {} {}", clasz.getName(), entityId, le.getEvents());
      List<Event> events = le.getEvents().stream().map(this::toEvent).collect(Collectors.toList());
      return new EntityWithMetadata<T>(
              new EntityIdAndVersion(entityId, le.getEvents().get(le.getEvents().size() - 1).getId()),
              events,
              Aggregates.recreateAggregate(clasz, events));
    } catch (RuntimeException e) {
      if (activityLogger.isDebugEnabled())
        activityLogger.error(String.format("Find entity failed: %s %s", clasz.getName(), entityId), e);
      throw e;
    }
  }

  @Override
  public <T extends Aggregate<T>> EntityIdAndVersion update(Class<T> clasz, EntityIdAndVersion entityIdAndVersion, List<Event> events) {
    return update(clasz, entityIdAndVersion, events, Optional.empty());
  }

  @Override
  public <T extends Aggregate<T>> EntityIdAndVersion update(Class<T> clasz, EntityIdAndVersion entityIdAndVersion, List<Event> events, UpdateOptions updateOptions) {
    return update(clasz, entityIdAndVersion, events, Optional.ofNullable(updateOptions));
  }

  private Event toEvent(EventIdTypeAndData eventIdTypeAndData) {
    try {
      return JSonMapper.fromJson(eventIdTypeAndData.getEventData(), (Class<Event>) Class.forName(eventIdTypeAndData.getEventType()));
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }


  @Override
  public <T extends Aggregate<T>> EntityIdAndVersion update(Class<T> clasz, EntityIdAndVersion entityIdAndVersion, List<Event> events, Optional<UpdateOptions> updateOptions) {
    try {
      List<EventTypeAndData> serializedEvents = events.stream().map(this::toEventTypeAndData).collect(Collectors.toList());
      EntityIdVersionAndEventIds result = aggregateCrud.update(new EntityIdAndType(entityIdAndVersion.getEntityId(), clasz.getName()),
              entityIdAndVersion.getEntityVersion(),
              serializedEvents,
              updateOptions);
      if (activityLogger.isDebugEnabled())
        activityLogger.debug("Updated entity: {} {} {}", clasz.getName(), result.getEntityId(), toSerializedEventsWithIds(serializedEvents, result.getEventIds()));

      return result.toEntityIdAndVersion();
    } catch (RuntimeException e) {
      if (activityLogger.isDebugEnabled())
        activityLogger.error(String.format("Update entity failed: %s %s", clasz.getName(), entityIdAndVersion), e);
      throw e;
    }
  }

  @Override
  public void subscribe(String subscriberId, Map<String, Set<String>> aggregatesAndEvents, SubscriberOptions subscriberOptions, Function<DispatchedEvent<Event>, CompletableFuture<?>> handler) {
    try {
      if (activityLogger.isDebugEnabled())
        activityLogger.debug("Subscribing {} {}", subscriberId, aggregatesAndEvents);
      aggregateEvents.subscribe(subscriberId, aggregatesAndEvents, subscriberOptions,
              se -> serializedEventDeserializer.toDispatchedEvent(se).map(handler::apply).orElse(CompletableFuture.completedFuture(null)));
      if (activityLogger.isDebugEnabled())
        activityLogger.debug("Subscribed {} {}", subscriberId, aggregatesAndEvents);
    } catch (Exception e) {
      if (activityLogger.isDebugEnabled())
        activityLogger.error(String.format("Subscribe failed: %s %s", subscriberId, aggregatesAndEvents), e);
      throw e;
    }
  }

}
