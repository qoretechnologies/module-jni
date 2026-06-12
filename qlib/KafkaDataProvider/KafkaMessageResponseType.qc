# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore KafkaDataProvider module definition

/** KafkaMessageResponseType.qc Copyright 2022 - 2026 Qore Technologies, s.r.o.

    Permission is hereby granted, free of charge, to any person obtaining a
    copy of this software and associated documentation files (the "Software"),
    to deal in the Software without restriction, including without limitation
    the rights to use, copy, modify, merge, publish, distribute, sublicense,
    and/or sell copies of the Software, and to permit persons to whom the
    Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in
    all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
    FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
    DEALINGS IN THE SOFTWARE.
*/

#! Contains all public definitions in the KafkaDataProvider module
public namespace KafkaDataProvider {
#! Kafka message response / record data type
public class KafkaMessageResponseType inherits HashDataType {
    private {
        #! Field descriptions
        const Fields = {
            "topic": {
                "display_name": "Topic",
                "type": StringType,
                "desc": "The topic the record was appended to",
            },
            "partition": {
                "display_name": "Partition",
                "type": IntType,
                "desc": "The partition the record was sent to",
            },
            "serialized_key_size": {
                "display_name": "Serialized Key Size",
                "type": IntType,
                "desc": "The size of the serialized, uncompressed key in bytes",
            },
            "serialized_value_size": {
                "display_name": "Serialized Value Size",
                "type": IntType,
                "desc": "The size of the serialized, uncompressed value in bytes",
            },
            "timestamp": {
                "display_name": "Timestamp",
                "type": UnixTimestampMsType,
                "desc": "The timestamp of the record in the topic/partition",
            },
        };
    }

    #! Creates the object
    constructor() {
        addQoreFields(Fields);
    }
}

#! Constant giving the Kafka event data type
public const KafkaMessageResponseType = new KafkaMessageResponseType();
}
