package io.github.baitian6641.cloudy.console

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.testing.WindowDisplayContract
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsoleWindowDisplayTest : WindowDisplayContract<WorkspaceActivity>(
    WorkspaceActivity::class.java, WindowOwner.CONSOLE, captureEvidence = true,
)
