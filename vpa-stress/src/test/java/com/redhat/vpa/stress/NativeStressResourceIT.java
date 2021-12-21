package com.redhat.vpa.stress;

import io.quarkus.test.junit.NativeImageTest;

@NativeImageTest
public class NativeStressResourceIT extends StressResourceTest {

    // Execute the same tests but in native mode.
}