# -*- mode: qore; indent-tabs-mode: nil -*-
#! Qore JakartaJmsDataProvider module definition

/** JmsSessionPool.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! contains all public definitions in the JakartaJmsDataProvider module
public namespace JakartaJmsDataProvider {
#! Thread-safe pool of JMS sessions
/** Provides lazy creation of sessions up to a configurable maximum, with blocking acquire and release.
*/
public class JmsSessionPool {
    private {
        #! The JMS Connection used to create sessions
        Connection jms_conn;

        #! Session acknowledgment mode
        int ack_mode;

        #! Maximum number of sessions
        int max_sessions;

        #! Available sessions
        list<Session> available = ();

        #! Number of sessions currently created (available + checked out)
        int created_count = 0;

        #! Mutex for thread safety
        Mutex m();

        #! Condition for waiting on session availability
        Condition cond();

        #! Whether the pool has been closed
        bool closed = False;
    }

    #! Creates the session pool
    /** @param jms_conn the JMS Connection to use for creating sessions
        @param ack_mode the session acknowledgment mode
        @param max_sessions the maximum number of sessions in the pool
    */
    constructor(Connection jms_conn, int ack_mode, int max_sessions = 4) {
        self.jms_conn = jms_conn;
        self.ack_mode = ack_mode;
        self.max_sessions = max_sessions;
    }

    #! Acquires a session from the pool, blocking if necessary
    /** @param timeout_ms maximum time to wait in milliseconds; -1 for infinite

        @return the acquired session

        @throw JMS-SESSION-POOL-ERROR if the pool is closed or timeout expires
    */
    Session acquire(int timeout_ms = 5000) {
        AutoLock al(m);
        if (closed) {
            throw "JMS-SESSION-POOL-ERROR", "session pool is closed";
        }

        # try to get from available pool first
        if (available.size()) {
            return shift available;
        }

        # create a new session if under the limit
        if (created_count < max_sessions) {
            ++created_count;
            # unlock while creating the session since it may block
            al.unlock();
            try {
                Session session = jms_conn.createSession(ack_mode);
                return session;
            } catch (hash<ExceptionInfo> ex) {
                al.lock();
                --created_count;
                rethrow;
            }
        }

        # wait for a session to become available; poll with 500ms intervals
        # for cooperative cancellation support
        date deadline = now_us() + milliseconds(timeout_ms);
        while (!available.size()) {
            if (closed) {
                throw "JMS-SESSION-POOL-ERROR", "session pool is closed";
            }
            int remaining_ms = (deadline - now_us()).durationMilliseconds();
            if (remaining_ms <= 0) {
                throw "JMS-SESSION-POOL-ERROR", sprintf("timeout waiting for a session after %d ms", timeout_ms);
            }
            # use bounded wait for cancellation responsiveness
            int wait_ms = remaining_ms > 500 ? 500 : remaining_ms;
            cond.wait(m, wait_ms);
            # cancellation point — allows program interrupt / thread cancel to take effect
            al.unlock();
            sleep(0);
            al.lock();
        }
        return shift available;
    }

    #! Releases a session back to the pool
    /** @param session the session to release
    */
    release(Session session) {
        AutoLock al(m);
        if (closed) {
            try {
                session.close();
            } catch () {
                # ignore
            }
            return;
        }
        push available, session;
        cond.signal();
    }

    #! Closes all sessions in the pool
    close() {
        AutoLock al(m);
        closed = True;
        foreach Session session in (available) {
            try {
                session.close();
            } catch () {
                # ignore
            }
        }
        available = ();
        created_count = 0;
        cond.broadcast();
    }

    #! Returns the number of available sessions
    int getAvailableCount() {
        AutoLock al(m);
        return available.size();
    }

    #! Returns the total number of created sessions
    int getCreatedCount() {
        AutoLock al(m);
        return created_count;
    }

    #! Returns True if the pool is closed
    bool isClosed() {
        AutoLock al(m);
        return closed;
    }
}
}
