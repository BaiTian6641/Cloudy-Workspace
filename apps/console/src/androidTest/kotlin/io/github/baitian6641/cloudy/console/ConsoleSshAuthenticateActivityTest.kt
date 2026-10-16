package io.github.baitian6641.cloudy.console

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.testing.SshAuthenticateActivityContract
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsoleSshAuthenticateActivityTest : SshAuthenticateActivityContract(WindowOwner.CONSOLE)
