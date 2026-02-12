/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.stream;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StreamWriterManager class.
 * 
 * <p>Converted from Python: test_manager.py</p>
 * <p>Python测试类: TestStreamWriterManagerInit, TestStreamWriterManagerCreateManager,
 *    TestStreamWriterManagerGetWriter, TestStreamWriterManagerAddWriter,
 *    TestStreamWriterManagerRemoveWriter, TestStreamWriterManagerStreamOutput</p>
 */
class StreamWriterManagerTest {
    
    private StreamEmitter streamEmitter;
    private StreamWriterManager manager;
    
    @BeforeEach
    void setUp() {
        streamEmitter = new StreamEmitter();
        manager = new StreamWriterManager(streamEmitter);
    }
    
    @Nested
    @DisplayName("StreamWriterManager Init Tests")
    class StreamWriterManagerInitTests {
        
        @Test
        @DisplayName("Should initialize with valid emitter")
        void testInitWithValidEmitter() {
            // Python: manager = StreamWriterManager(stream_emitter)
            //         assert manager.stream_emitter() is stream_emitter
            StreamEmitter emitter = new StreamEmitter();
            StreamWriterManager mgr = new StreamWriterManager(emitter);
            
            assertSame(emitter, mgr.streamEmitter());
        }
        
        @Test
        @DisplayName("Should raise ValueError when emitter is None")
        void testInitWithNoneEmitterRaises() {
            // Python: with pytest.raises(ValueError, match="stream_emitter is None"):
            //             StreamWriterManager(None)
            assertThrows(IllegalArgumentException.class, () -> 
                new StreamWriterManager(null));
        }
        
        @Test
        @DisplayName("Should create default OUTPUT, TRACE, CUSTOM writers")
        void testInitCreatesDefaultWriters() {
            // Python: assert manager.get_output_writer() is not None
            //         assert manager.get_trace_writer() is not None
            //         assert manager.get_custom_writer() is not None
            assertNotNull(manager.getOutputWriter());
            assertNotNull(manager.getTraceWriter());
            assertNotNull(manager.getCustomWriter());
        }
        
        @Test
        @DisplayName("Should create only specified writers")
        void testInitWithCustomModes() {
            // Python: manager = StreamWriterManager(stream_emitter, modes=[BaseStreamMode.OUTPUT])
            //         assert manager.get_output_writer() is not None
            StreamEmitter emitter = new StreamEmitter();
            StreamWriterManager mgr = new StreamWriterManager(emitter, List.of(BaseStreamMode.OUTPUT));
            
            assertNotNull(mgr.getOutputWriter());
        }
    }
    
    @Nested
    @DisplayName("StreamWriterManager CreateManager Tests")
    class StreamWriterManagerCreateManagerTests {
        
        @Test
        @DisplayName("Should create manager via factory method")
        void testCreateManager() {
            // Python: manager = StreamWriterManager.create_manager(stream_emitter)
            //         assert manager is not None
            //         assert isinstance(manager, StreamWriterManager)
            StreamEmitter emitter = new StreamEmitter();
            StreamWriterManager mgr = StreamWriterManager.createManager(emitter);
            
            assertNotNull(mgr);
            assertInstanceOf(StreamWriterManager.class, mgr);
        }
    }
    
    @Nested
    @DisplayName("StreamWriterManager GetWriter Tests")
    class StreamWriterManagerGetWriterTests {
        
        @Test
        @DisplayName("Should return output writer")
        void testGetOutputWriter() {
            // Python: writer = manager.get_output_writer()
            //         assert isinstance(writer, OutputStreamWriter)
            var writer = manager.getOutputWriter();
            
            assertInstanceOf(OutputStreamWriter.class, writer);
        }
        
        @Test
        @DisplayName("Should return trace writer")
        void testGetTraceWriter() {
            // Python: writer = manager.get_trace_writer()
            //         assert isinstance(writer, TraceStreamWriter)
            var writer = manager.getTraceWriter();
            
            assertInstanceOf(TraceStreamWriter.class, writer);
        }
        
        @Test
        @DisplayName("Should return custom writer")
        void testGetCustomWriter() {
            // Python: writer = manager.get_custom_writer()
            //         assert isinstance(writer, CustomStreamWriter)
            var writer = manager.getCustomWriter();
            
            assertInstanceOf(CustomStreamWriter.class, writer);
        }
        
        @Test
        @DisplayName("Should return writer by mode")
        void testGetWriterWithMode() {
            // Python: writer = manager.get_writer(BaseStreamMode.OUTPUT)
            //         assert writer is not None
            var writer = manager.getWriter(BaseStreamMode.OUTPUT);
            
            assertNotNull(writer);
        }
        
        @Test
        @DisplayName("Should return None for non-existent mode")
        void testGetWriterNonexistentMode() {
            // Python: writer = manager.get_writer(mock_mode)
            //         assert writer is None
            // Note: StreamMode is an enum in Java, so we test with a mode that exists 
            // but wasn't added to the manager. We use a fresh manager with only OUTPUT mode.
            StreamEmitter emitter = new StreamEmitter();
            StreamWriterManager mgr = new StreamWriterManager(emitter, List.of(StreamMode.OUTPUT));
            
            // TRACE mode was not added to this manager
            var writer = mgr.getWriter(StreamMode.TRACE);
            
            assertNull(writer);
        }
    }
    
    @Nested
    @DisplayName("StreamWriterManager AddWriter Tests")
    class StreamWriterManagerAddWriterTests {
        
        @Test
        @DisplayName("Should add new writer")
        void testAddWriter() {
            // Python: manager.add_writer(mock_mode, mock_writer)
            //         assert manager.get_writer(mock_mode) is mock_writer
            // Create a manager without TRACE mode, then add it
            StreamEmitter emitter = new StreamEmitter();
            StreamWriterManager mgr = new StreamWriterManager(emitter, List.of(StreamMode.OUTPUT));
            
            // Create a real writer instead of mocking
            TraceStreamWriter traceWriter = new TraceStreamWriter(emitter);
            mgr.addWriter(StreamMode.TRACE, traceWriter);
            
            assertSame(traceWriter, mgr.getWriter(StreamMode.TRACE));
        }
    }
    
    @Nested
    @DisplayName("StreamWriterManager RemoveWriter Tests")
    class StreamWriterManagerRemoveWriterTests {
        
        @Test
        @DisplayName("Should raise ValueError when removing default writer")
        void testRemoveDefaultWriterRaises() {
            // Python: with pytest.raises(ValueError, match="Can not remove default writer"):
            //             manager.remove_writer(BaseStreamMode.OUTPUT)
            assertThrows(IllegalArgumentException.class, () -> 
                manager.removeWriter(BaseStreamMode.OUTPUT));
        }
        
        @Test
        @DisplayName("Should remove non-default writer")
        void testRemoveCustomWriter() {
            // Python: manager.add_writer(mock_mode, mock_writer)
            //         removed = manager.remove_writer(mock_mode)
            //         assert removed is mock_writer
            //         assert manager.get_writer(mock_mode) is None
            // Create a manager without TRACE mode, then add and remove it
            StreamEmitter emitter = new StreamEmitter();
            StreamWriterManager mgr = new StreamWriterManager(emitter, List.of(StreamMode.OUTPUT));
            
            // Create a real writer instead of mocking
            TraceStreamWriter traceWriter = new TraceStreamWriter(emitter);
            mgr.addWriter(StreamMode.TRACE, traceWriter);
            
            var removed = mgr.removeWriter(StreamMode.TRACE);
            
            assertSame(traceWriter, removed);
            assertNull(mgr.getWriter(StreamMode.TRACE));
        }
    }
    
    @Nested
    @DisplayName("StreamWriterManager StreamOutput Tests")
    class StreamWriterManagerStreamOutputTests {
        
        @Test
        @DisplayName("Should yield received data")
        void testStreamOutputReceivesData() throws Exception {
            // Python: async for data in manager.stream_output(...):
            //             results.append(data)
            //         assert {"test": "data"} in results
            StreamEmitter emitter = new StreamEmitter();
            StreamWriterManager mgr = new StreamWriterManager(emitter);
            
            // Send data in background
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(10);
                    emitter.emit(Map.of("test", "data")).get();
                    emitter.close().get();
                } catch (Exception e) {
                    // Ignore
                }
            });
            
            List<Object> results = new ArrayList<>();
            // streamOutput returns Iterable<Object> in Java
            for (Object data : mgr.streamOutput(1, 1, false)) {
                results.add(data);
            }
            
            // Verify stream processed successfully
            assertTrue(results.contains(Map.of("test", "data")));
        }
        
        @Test
        @DisplayName("Should raise exception on first frame timeout")
        void testStreamOutputFirstFrameTimeout() {
            // Python: with pytest.raises(JiuWenBaseException):
            //             async for _ in manager.stream_output(first_frame_timeout=0.01):
            //                 pass
            StreamEmitter emitter = new StreamEmitter();
            StreamWriterManager mgr = new StreamWriterManager(emitter);
            
            // Very short timeout should cause exception when iterating
            assertThrows(JiuWenBaseException.class, () -> {
                for (Object data : mgr.streamOutput(0.01, 10, true)) {
                    // Should throw before yielding any data
                }
            });
        }
        
        @Test
        @DisplayName("Should stop when receiving END_FRAME")
        void testStreamOutputStopsOnEndFrame() throws Exception {
            // Python: async for data in manager.stream_output(first_frame_timeout=1, timeout=1, need_close=False):
            //             results.append(data)
            //         assert True
            StreamEmitter emitter = new StreamEmitter();
            StreamWriterManager mgr = new StreamWriterManager(emitter);
            
            // Close immediately to send END_FRAME
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(10);
                    emitter.close().get();
                } catch (Exception e) {
                    // Ignore
                }
            });
            
            List<Object> results = new ArrayList<>();
            // Should complete without error
            assertDoesNotThrow(() -> {
                for (Object data : mgr.streamOutput(1, 1, false)) {
                    results.add(data);
                }
            });
        }
    }
}

