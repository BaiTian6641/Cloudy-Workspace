package io.github.baitian6641.cloudy.platform

import android.os.IBinder
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Parcel boundary policy, not caller authentication or cross-process dispatch evidence. */
@RunWith(AndroidJUnit4::class)
class FilesHandoffParcelTest {
    @Test fun validGeneratedShapesLeaveOriginalCursorUntouched() {
        parcel { data ->
            data.writeInt(1); data.writeString(""); data.setDataPosition(0)
            FilesHandoffParcel.validate(IBinder.FIRST_CALL_TRANSACTION, data, 0, true)
            assertEquals(0, data.dataPosition())
        }
        parcel { data ->
            data.writeByteArray(ByteArray(HandoffJson.MAX_BYTES)); data.setDataPosition(0)
            FilesHandoffParcel.validate(IBinder.FIRST_CALL_TRANSACTION + 1, data, 0, true)
            assertEquals(0, data.dataPosition())
        }
    }

    @Test fun trailingDataIsRejectedBeforeGeneratedDispatch() = parcel { data ->
        data.writeInt(1); data.writeString(""); data.writeInt(7); data.setDataPosition(0)
        denied { FilesHandoffParcel.validate(IBinder.FIRST_CALL_TRANSACTION, data, 0, true) }
        assertEquals(0, data.dataPosition())
    }

    @Test fun wrongDescriptorAndUnknownTransactionAreRejected() {
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken("unrelated"); data.writeInt(1); data.writeString(""); data.setDataPosition(0)
            denied { FilesHandoffParcel.validate(IBinder.FIRST_CALL_TRANSACTION, data, 0, true) }
        } finally { data.recycle() }
        parcel { valid ->
            valid.writeInt(1); valid.writeString(""); valid.setDataPosition(0)
            denied { FilesHandoffParcel.validate(IBinder.FIRST_CALL_TRANSACTION + 2, valid, 0, true) }
        }
    }

    @Test fun oneWayCallsAreRejectedEvenWhenReplyExists() = parcel { data ->
        data.writeInt(1); data.writeString(""); data.setDataPosition(0)
        denied { FilesHandoffParcel.validate(IBinder.FIRST_CALL_TRANSACTION, data, IBinder.FLAG_ONEWAY, true) }
        // A non-oneway test bit also exercises the mask when flags are combined.
        denied { FilesHandoffParcel.validate(IBinder.FIRST_CALL_TRANSACTION, data, IBinder.FLAG_ONEWAY or (1 shl 1), true) }
        assertEquals(0, data.dataPosition())
    }

    @Test fun synchronousCallsRequireReply() = parcel { data ->
        data.writeInt(1); data.writeString(""); data.setDataPosition(0)
        denied { FilesHandoffParcel.validate(IBinder.FIRST_CALL_TRANSACTION, data, 0, false) }
        assertEquals(0, data.dataPosition())
    }

    @Test fun nonOneWayFlagBitPreservesValidSynchronousShape() = parcel { data ->
        data.writeInt(1); data.writeString(""); data.setDataPosition(0)
        // Representative non-oneway bit, not a claim about a named SDK flag.
        FilesHandoffParcel.validate(IBinder.FIRST_CALL_TRANSACTION, data, 1 shl 1, true)
        assertEquals(0, data.dataPosition())
    }

    @Test fun decodedAndTotalBoundsRejectExcessInput() {
        parcel { data ->
            data.writeByteArray(ByteArray(HandoffJson.MAX_BYTES + 1)); data.setDataPosition(0)
            denied { FilesHandoffParcel.validate(IBinder.FIRST_CALL_TRANSACTION + 1, data, 0, true) }
        }
        parcel { data ->
            data.writeByteArray(ByteArray(HandoffJson.MAX_BYTES + 1_024)); data.setDataPosition(0)
            denied { FilesHandoffParcel.validate(IBinder.FIRST_CALL_TRANSACTION + 1, data, 0, true) }
        }
    }

    private fun parcel(block: (Parcel) -> Unit) {
        val data = Parcel.obtain()
        try { data.writeInterfaceToken(ICloudyFilesHandoff.DESCRIPTOR); block(data) }
        finally { data.recycle() }
    }
    private fun denied(block: () -> Unit) { assertThrows(SecurityException::class.java, block) }
}
