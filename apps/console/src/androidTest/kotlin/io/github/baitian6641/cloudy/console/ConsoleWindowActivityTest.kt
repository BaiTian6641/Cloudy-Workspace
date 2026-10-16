package io.github.baitian6641.cloudy.console

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.testing.WindowActivityContract
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsoleWindowActivityTest : WindowActivityContract<WorkspaceActivity>(
    WorkspaceActivity::class.java, WindowOwner.CONSOLE, R.string.app_name, R.string.default_window_name,
)
