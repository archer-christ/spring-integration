/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.splitter;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;
import org.reactivestreams.Subscriber;

import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.FluxMessageChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.endpoint.EventDrivenConsumer;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * @author Mark Fisher
 * @author Iwein Fuld
 * @author Gunnar Hillert
 * @author Artem Bilan
 */
public class DefaultSplitterTests {

	@Test
	public void splitMessageWithArrayPayload() throws Exception {
		String[] payload = new String[] { "x", "y", "z" };
		Message<String[]> message = MessageBuilder.withPayload(payload).build();
		QueueChannel replyChannel = new QueueChannel();
		DefaultMessageSplitter splitter = new DefaultMessageSplitter();
		splitter.setOutputChannel(replyChannel);
		splitter.handleMessage(message);
		List<Message<?>> replies = replyChannel.clear();
		assertEquals(3, replies.size());
		Message<?> reply1 = replies.get(0);
		assertNotNull(reply1);
		assertEquals("x", reply1.getPayload());
		Message<?> reply2 = replies.get(1);
		assertNotNull(reply2);
		assertEquals("y", reply2.getPayload());
		Message<?> reply3 = replies.get(2);
		assertNotNull(reply3);
		assertEquals("z", reply3.getPayload());
	}

	@Test
	public void splitMessageWithCollectionPayload() throws Exception {
		List<String> payload = Arrays.asList("x", "y", "z");
		Message<List<String>> message = MessageBuilder.withPayload(payload).build();
		QueueChannel replyChannel = new QueueChannel();
		DefaultMessageSplitter splitter = new DefaultMessageSplitter();
		splitter.setOutputChannel(replyChannel);
		splitter.handleMessage(message);
		List<Message<?>> replies = replyChannel.clear();
		assertEquals(3, replies.size());
		Message<?> reply1 = replies.get(0);
		assertNotNull(reply1);
		assertEquals("x", reply1.getPayload());
		Message<?> reply2 = replies.get(1);
		assertNotNull(reply2);
		assertEquals("y", reply2.getPayload());
		Message<?> reply3 = replies.get(2);
		assertNotNull(reply3);
		assertEquals("z", reply3.getPayload());
	}

	@Test
	public void correlationIdCopiedFromMessageId() {
		Message<String> message = MessageBuilder.withPayload("test").build();
		DirectChannel inputChannel = new DirectChannel();
		QueueChannel outputChannel = new QueueChannel(1);
		DefaultMessageSplitter splitter = new DefaultMessageSplitter();
		splitter.setOutputChannel(outputChannel);
		EventDrivenConsumer endpoint = new EventDrivenConsumer(inputChannel, splitter);
		endpoint.start();
		assertTrue(inputChannel.send(message));
		Message<?> reply = outputChannel.receive(0);
		assertEquals(message.getHeaders().getId(), new IntegrationMessageHeaderAccessor(reply).getCorrelationId());
	}

	@Test
	public void splitMessageWithEmptyCollectionPayload() throws Exception {
		Message<List<String>> message = MessageBuilder.withPayload(Collections.<String>emptyList()).build();
		QueueChannel replyChannel = new QueueChannel();
		DefaultMessageSplitter splitter = new DefaultMessageSplitter();
		splitter.setOutputChannel(replyChannel);
		splitter.handleMessage(message);
		Message<?> output = replyChannel.receive(15);
		assertThat(output, is(nullValue()));
	}

	@Test
	public void splitStream() {
		Message<?> message = new GenericMessage<>(
				Stream.generate(Math::random)
						.limit(10));
		QueueChannel outputChannel = new QueueChannel();
		DefaultMessageSplitter splitter = new DefaultMessageSplitter();
		splitter.setOutputChannel(outputChannel);
		splitter.handleMessage(message);
		for (int i = 0; i < 10; i++) {
			Message<?> reply = outputChannel.receive(0);
			assertEquals(message.getHeaders().getId(), new IntegrationMessageHeaderAccessor(reply).getCorrelationId());
		}
		assertNull(outputChannel.receive(10));
	}

	@Test
	public void splitFlux() {
		Message<?> message = new GenericMessage<>(
				Flux
						.generate(() -> 0,
								(state, sink) -> {
									if (state == 10) {
										sink.complete();
									}
									else {
										sink.next(state);
									}
									return ++state;
								}));
		QueueChannel outputChannel = new QueueChannel();
		DefaultMessageSplitter splitter = new DefaultMessageSplitter();
		splitter.setOutputChannel(outputChannel);
		splitter.handleMessage(message);
		for (int i = 0; i < 10; i++) {
			Message<?> reply = outputChannel.receive(0);
			assertEquals(message.getHeaders().getId(), new IntegrationMessageHeaderAccessor(reply).getCorrelationId());
		}
		assertNull(outputChannel.receive(10));
	}

	@Test
	public void splitArrayPayloadReactive() {
		Message<?> message = new GenericMessage<>(new String[] { "x", "y", "z" });
		FluxMessageChannel replyChannel = new FluxMessageChannel();
		DefaultMessageSplitter splitter = new DefaultMessageSplitter();
		splitter.setOutputChannel(replyChannel);

		splitter.handleMessage(message);

		Flux<String> testFlux =
				Flux.from(replyChannel)
						.map(Message::getPayload)
						.cast(String.class);

		StepVerifier.create(testFlux)
				.expectNext("x", "y", "z")
				.then(() ->
						((Subscriber<?>) TestUtils.getPropertyValue(replyChannel, "subscribers", List.class).get(0))
								.onComplete())
				.verifyComplete();
	}

	@Test
	public void splitStreamReactive() {
		Message<?> message = new GenericMessage<>(Stream.of("x", "y", "z"));
		FluxMessageChannel replyChannel = new FluxMessageChannel();
		DefaultMessageSplitter splitter = new DefaultMessageSplitter();
		splitter.setOutputChannel(replyChannel);

		splitter.handleMessage(message);

		Flux<String> testFlux =
				Flux.from(replyChannel)
						.map(Message::getPayload)
						.cast(String.class);

		StepVerifier.create(testFlux)
				.expectNext("x", "y", "z")
				.then(() ->
						((Subscriber<?>) TestUtils.getPropertyValue(replyChannel, "subscribers", List.class).get(0))
								.onComplete())
				.verifyComplete();
	}

	@Test
	public void splitFluxReactive() {
		Message<?> message = new GenericMessage<>(Flux.just("x", "y", "z"));
		FluxMessageChannel replyChannel = new FluxMessageChannel();
		DefaultMessageSplitter splitter = new DefaultMessageSplitter();
		splitter.setOutputChannel(replyChannel);

		splitter.handleMessage(message);

		Flux<String> testFlux =
				Flux.from(replyChannel)
						.map(Message::getPayload)
						.cast(String.class);

		StepVerifier.create(testFlux)
				.expectNext("x", "y", "z")
				.then(() ->
						((Subscriber<?>) TestUtils.getPropertyValue(replyChannel, "subscribers", List.class).get(0))
								.onComplete())
				.verifyComplete();
	}

}
