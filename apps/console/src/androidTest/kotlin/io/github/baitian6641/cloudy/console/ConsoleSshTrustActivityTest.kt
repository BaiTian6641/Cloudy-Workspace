package io.github.baitian6641.cloudy.console

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.testing.SshTrustActivityContract
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsoleSshTrustActivityTest : SshTrustActivityContract(WindowOwner.CONSOLE)
