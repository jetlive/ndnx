/**
 * @file ccnr_dispatch.c
 * 
 * Part of ccnr -  CCNx Repository Daemon.
 *
 */

/*
 * Copyright (C) 2011 Palo Alto Research Center, Inc.
 *
 * This work is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 as published by the
 * Free Software Foundation.
 * This work is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details. You should have received a copy of the GNU General Public
 * License along with this program; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301, USA.
 */
 
#include "common.h"

static void
process_incoming_interest(struct ccnr_handle *h, struct fdholder *fdholder,
                          unsigned char *msg, size_t size)
{
    struct hashtb_enumerator ee;
    struct hashtb_enumerator *e = &ee;
    struct ccn_parsed_interest parsed_interest = {0};
    struct ccn_parsed_interest *pi = &parsed_interest;
    size_t namesize = 0;
    int k;
    int res;
    int try;
    int matched;
    int s_ok;
    struct nameprefix_entry *npe = NULL;
    struct content_entry *content = NULL;
    struct content_entry *last_match = NULL;
    struct ccn_indexbuf *comps = r_util_indexbuf_obtain(h);
    if (size > 65535)
        res = -__LINE__;
    else
        res = ccn_parse_interest(msg, size, pi, comps);
    if (res < 0) {
        ccnr_msg(h, "error parsing Interest - code %d", res);
        ccn_indexbuf_destroy(&comps);
        return;
    }
    ccnr_meter_bump(h, fdholder->meter[FM_INTI], 1);
    if (pi->scope >= 0 && pi->scope < 2 &&
             (fdholder->flags & CCN_FACE_GG) == 0) {
        ccnr_debug_ccnb(h, __LINE__, "interest_outofscope", fdholder, msg, size);
        h->interests_dropped += 1;
    }
    else if (r_fwd_is_duplicate_flooded(h, msg, pi, fdholder->filedesc)) {
        if (h->debug & 16)
             ccnr_debug_ccnb(h, __LINE__, "interest_dup", fdholder, msg, size);
        h->interests_dropped += 1;
    }
    else {
        if (h->debug & (16 | 8 | 2))
            ccnr_debug_ccnb(h, __LINE__, "interest_from", fdholder, msg, size);
        if (h->debug & 16)
            ccnr_msg(h,
                     "version: %d, "
                     "prefix_comps: %d, "
                     "min_suffix_comps: %d, "
                     "max_suffix_comps: %d, "
                     "orderpref: %d, "
                     "answerfrom: %d, "
                     "scope: %d, "
                     "lifetime: %d.%04d, "
                     "excl: %d bytes, "
                     "etc: %d bytes",
                     pi->magic,
                     pi->prefix_comps,
                     pi->min_suffix_comps,
                     pi->max_suffix_comps,
                     pi->orderpref, pi->answerfrom, pi->scope,
                     ccn_interest_lifetime_seconds(msg, pi),
                     (int)(ccn_interest_lifetime(msg, pi) & 0xFFF) * 10000 / 4096,
                     pi->offset[CCN_PI_E_Exclude] - pi->offset[CCN_PI_B_Exclude],
                     pi->offset[CCN_PI_E_OTHER] - pi->offset[CCN_PI_B_OTHER]);
        if (pi->magic < 20090701) {
            if (++(h->oldformatinterests) == h->oldformatinterestgrumble) {
                h->oldformatinterestgrumble *= 2;
                ccnr_msg(h, "downrev interests received: %d (%d)",
                         h->oldformatinterests,
                         pi->magic);
            }
        }
        namesize = comps->buf[pi->prefix_comps] - comps->buf[0];
        h->interests_accepted += 1;
        s_ok = (pi->answerfrom & CCN_AOK_STALE) != 0;
        matched = 0;
        hashtb_start(h->nameprefix_tab, e);
        res = r_fwd_nameprefix_seek(h, e, msg, comps, pi->prefix_comps);
        npe = e->data;
        if (npe == NULL)
            goto Bail;
        if ((npe->flags & CCN_FORW_LOCAL) != 0 &&
            (fdholder->flags & CCN_FACE_GG) == 0) {
            ccnr_debug_ccnb(h, __LINE__, "interest_nonlocal", fdholder, msg, size);
            h->interests_dropped += 1;
            goto Bail;
        }
        if ((pi->answerfrom & CCN_AOK_CS) != 0) {
            last_match = NULL;
            content = r_store_find_first_match_candidate(h, msg, pi);
            if (content != NULL && (h->debug & 8))
                ccnr_debug_ccnb(h, __LINE__, "first_candidate", NULL,
                                content->key,
                                content->size);
            if (content != NULL &&
                !r_store_content_matches_interest_prefix(h, content, msg, comps,
                                                 pi->prefix_comps)) {
                if (h->debug & 8)
                    ccnr_debug_ccnb(h, __LINE__, "prefix_mismatch", NULL,
                                    msg, size);
                content = NULL;
            }
            for (try = 0; content != NULL; try++) {
                if ((s_ok || (content->flags & CCN_CONTENT_ENTRY_STALE) == 0) &&
                    ccn_content_matches_interest(content->key,
                                       content->size,
                                       0, NULL, msg, size, pi)) {
                    if ((pi->orderpref & 1) == 0 && // XXX - should be symbolic
                        pi->prefix_comps != comps->n - 1 &&
                        comps->n == content->ncomps &&
                        r_store_content_matches_interest_prefix(h, content, msg,
                                                        comps, comps->n - 1)) {
                        if (h->debug & 8)
                            ccnr_debug_ccnb(h, __LINE__, "skip_match", NULL,
                                            content->key,
                                            content->size);
                        goto move_along;
                    }
                    if (h->debug & 8)
                        ccnr_debug_ccnb(h, __LINE__, "matches", NULL,
                                        content->key,
                                        content->size);
                    if ((pi->orderpref & 1) == 0) // XXX - should be symbolic
                        break;
                    last_match = content;
                    content = r_store_next_child_at_level(h, content, comps->n - 1);
                    goto check_next_prefix;
                }
            move_along:
                content = r_store_content_from_accession(h, r_store_content_skiplist_next(h, content));
            check_next_prefix:
                if (content != NULL &&
                    !r_store_content_matches_interest_prefix(h, content, msg,
                                                     comps, pi->prefix_comps)) {
                    if (h->debug & 8)
                        ccnr_debug_ccnb(h, __LINE__, "prefix_mismatch", NULL,
                                        content->key,
                                        content->size);
                    content = NULL;
                }
            }
            if (last_match != NULL)
                content = last_match;
            if (content != NULL) {
                /* Check to see if we are planning to send already */
                enum cq_delay_class c;
                for (c = 0, k = -1; c < CCN_CQ_N && k == -1; c++)
                    if (fdholder->q[c] != NULL)
                        k = ccn_indexbuf_member(fdholder->q[c]->send_queue, content->accession);
                if (k == -1) {
                    k = r_sendq_face_send_queue_insert(h, fdholder, content);
                    if (k >= 0) {
                        if (h->debug & (32 | 8))
                            ccnr_debug_ccnb(h, __LINE__, "consume", fdholder, msg, size);
                    }
                    /* Any other matched interests need to be consumed, too. */
                    r_match_match_interests(h, content, NULL, fdholder, NULL);
                }
                if ((pi->answerfrom & CCN_AOK_EXPIRE) != 0)
                    r_store_mark_stale(h, content);
                matched = 1;
            }
        }
        if (!matched && pi->scope != 0 && npe != NULL)
            r_fwd_propagate_interest(h, fdholder, msg, pi, npe);
    Bail:
        hashtb_end(e);
    }
    r_util_indexbuf_release(h, comps);
}

static void
process_incoming_content(struct ccnr_handle *h, struct fdholder *fdholder,
                         unsigned char *wire_msg, size_t wire_size)
{
    unsigned char *msg;
    size_t size;
    struct hashtb_enumerator ee;
    struct hashtb_enumerator *e = &ee;
    struct ccn_parsed_ContentObject obj = {0};
    int res;
    size_t keysize = 0;
    size_t tailsize = 0;
    unsigned char *tail = NULL;
    struct content_entry *content = NULL;
    int i;
    struct ccn_indexbuf *comps = r_util_indexbuf_obtain(h);
    struct ccn_charbuf *cb = r_util_charbuf_obtain(h);
    
    msg = wire_msg;
    size = wire_size;
    
    res = ccn_parse_ContentObject(msg, size, &obj, comps);
    if (res < 0) {
        ccnr_msg(h, "error parsing ContentObject - code %d", res);
        goto Bail;
    }
    ccnr_meter_bump(h, fdholder->meter[FM_DATI], 1);
    if (comps->n < 1 ||
        (keysize = comps->buf[comps->n - 1]) > 65535 - 36) {
        ccnr_msg(h, "ContentObject with keysize %lu discarded",
                 (unsigned long)keysize);
        ccnr_debug_ccnb(h, __LINE__, "oversize", fdholder, msg, size);
        res = -__LINE__;
        goto Bail;
    }
    /* Make the ContentObject-digest name component explicit */
    ccn_digest_ContentObject(msg, &obj);
    if (obj.digest_bytes != 32) {
        ccnr_debug_ccnb(h, __LINE__, "indigestible", fdholder, msg, size);
        goto Bail;
    }
    i = comps->buf[comps->n - 1];
    ccn_charbuf_append(cb, msg, i);
    ccn_charbuf_append_tt(cb, CCN_DTAG_Component, CCN_DTAG);
    ccn_charbuf_append_tt(cb, obj.digest_bytes, CCN_BLOB);
    ccn_charbuf_append(cb, obj.digest, obj.digest_bytes);
    ccn_charbuf_append_closer(cb);
    ccn_charbuf_append(cb, msg + i, size - i);
    msg = cb->buf;
    size = cb->length;
    res = ccn_parse_ContentObject(msg, size, &obj, comps);
    if (res < 0) abort(); /* must have just messed up */
    
    if (obj.magic != 20090415) {
        if (++(h->oldformatcontent) == h->oldformatcontentgrumble) {
            h->oldformatcontentgrumble *= 10;
            ccnr_msg(h, "downrev content items received: %d (%d)",
                     h->oldformatcontent,
                     obj.magic);
        }
    }
    if (h->debug & 4)
        ccnr_debug_ccnb(h, __LINE__, "content_from", fdholder, msg, size);
    keysize = obj.offset[CCN_PCO_B_Content];
    tail = msg + keysize;
    tailsize = size - keysize;
    hashtb_start(h->content_tab, e);
    res = hashtb_seek(e, msg, keysize, tailsize);
    content = e->data;
    if (res == HT_OLD_ENTRY) {
        if (tailsize != e->extsize ||
              0 != memcmp(tail, ((unsigned char *)e->key) + keysize, tailsize)) {
            ccnr_msg(h, "ContentObject name collision!!!!!");
            ccnr_debug_ccnb(h, __LINE__, "new", fdholder, msg, size);
            ccnr_debug_ccnb(h, __LINE__, "old", NULL, e->key, e->keysize + e->extsize);
            content = NULL;
            hashtb_delete(e); /* XXX - Mercilessly throw away both of them. */
            res = -__LINE__;
        }
        else if ((content->flags & CCN_CONTENT_ENTRY_STALE) != 0) {
            /* When old content arrives after it has gone stale, freshen it */
            // XXX - ought to do mischief checks before this
            content->flags &= ~CCN_CONTENT_ENTRY_STALE;
            h->n_stale--;
            r_store_set_content_timer(h, content, &obj);
            // XXX - no counter for this case
        }
        else {
            h->content_dups_recvd++;
            ccnr_msg(h, "received duplicate ContentObject from %u (accession %llu)",
                     fdholder->filedesc, (unsigned long long)content->accession);
            ccnr_debug_ccnb(h, __LINE__, "dup", fdholder, msg, size);
        }
    }
    else if (res == HT_NEW_ENTRY) {
        content->accession = ++(h->accession);
        r_store_enroll_content(h, content);
        if (content == r_store_content_from_accession(h, content->accession)) {
            content->ncomps = comps->n;
            content->comps = calloc(comps->n, sizeof(comps[0]));
        }
        content->key_size = e->keysize;
        content->size = e->keysize + e->extsize;
        content->key = e->key;
        if (content->comps != NULL) {
            for (i = 0; i < comps->n; i++)
                content->comps[i] = comps->buf[i];
            r_store_content_skiplist_insert(h, content);
            r_store_set_content_timer(h, content, &obj);
        }
        else {
            ccnr_msg(h, "could not enroll ContentObject (accession %llu)",
                (unsigned long long)content->accession);
            hashtb_delete(e);
            res = -__LINE__;
            content = NULL;
        }
        /* Mark public keys supplied at startup as precious. */
        if (obj.type == CCN_CONTENT_KEY && content->accession <= (h->capacity + 7)/8)
            content->flags |= CCN_CONTENT_ENTRY_PRECIOUS;
    }
    hashtb_end(e);
Bail:
    r_util_indexbuf_release(h, comps);
    r_util_charbuf_release(h, cb);
    cb = NULL;
    if (res >= 0 && content != NULL) {
        int n_matches;
        enum cq_delay_class c;
        struct content_queue *q;
        n_matches = r_match_match_interests(h, content, &obj, NULL, fdholder);
        if (res == HT_NEW_ENTRY) {
            if (n_matches < 0) {
                r_store_remove_content(h, content);
                return;
            }
            if (n_matches == 0 && (fdholder->flags & CCN_FACE_GG) == 0) {
                content->flags |= CCN_CONTENT_ENTRY_SLOWSEND;
                ccn_indexbuf_append_element(h->unsol, content->accession);
            }
        }
        for (c = 0; c < CCN_CQ_N; c++) {
            q = fdholder->q[c];
            if (q != NULL) {
                i = ccn_indexbuf_member(q->send_queue, content->accession);
                if (i >= 0) {
                    /*
                     * In the case this consumed any interests from this source,
                     * don't send the content back
                     */
                    if (h->debug & 8)
                        ccnr_debug_ccnb(h, __LINE__, "content_nosend", fdholder, msg, size);
                    q->send_queue->buf[i] = 0;
                }
            }
        }
    }
}

static void
process_input_message(struct ccnr_handle *h, struct fdholder *fdholder,
                      unsigned char *msg, size_t size, int pdu_ok)
{
    struct ccn_skeleton_decoder decoder = {0};
    struct ccn_skeleton_decoder *d = &decoder;
    ssize_t dres;
    enum ccn_dtag dtag;
    
    if ((fdholder->flags & CCN_FACE_UNDECIDED) != 0) {
        fdholder->flags &= ~CCN_FACE_UNDECIDED;
        if ((fdholder->flags & CCN_FACE_LOOPBACK) != 0)
            fdholder->flags |= CCN_FACE_GG;
        /* YYY This is the first place that we know that an inbound stream fdholder is speaking CCNx protocol. */
        r_io_register_new_face(h, fdholder);
    }
    d->state |= CCN_DSTATE_PAUSE;
    dres = ccn_skeleton_decode(d, msg, size);
    if (d->state < 0)
        abort(); /* cannot happen because of checks in caller */
    if (CCN_GET_TT_FROM_DSTATE(d->state) != CCN_DTAG) {
        ccnr_msg(h, "discarding unknown message; size = %lu", (unsigned long)size);
        // XXX - keep a count?
        return;
    }
    dtag = d->numval;
    switch (dtag) {
        case CCN_DTAG_CCNProtocolDataUnit:
            if (!pdu_ok)
                break;
            size -= d->index;
            if (size > 0)
                size--;
            msg += d->index;
            fdholder->flags |= CCN_FACE_LINK;
            fdholder->flags &= ~CCN_FACE_GG;
            memset(d, 0, sizeof(*d));
            while (d->index < size) {
                dres = ccn_skeleton_decode(d, msg + d->index, size - d->index);
                if (d->state != 0)
                    abort(); /* cannot happen because of checks in caller */
                /* The pdu_ok parameter limits the recursion depth */
                process_input_message(h, fdholder, msg + d->index - dres, dres, 0);
            }
            return;
        case CCN_DTAG_Interest:
            process_incoming_interest(h, fdholder, msg, size);
            return;
        case CCN_DTAG_ContentObject:
            process_incoming_content(h, fdholder, msg, size);
            return;
        case CCN_DTAG_SequenceNumber:
            r_link_process_incoming_link_message(h, fdholder, dtag, msg, size);
            return;
        default:
            break;
    }
    ccnr_msg(h, "discarding unknown message; dtag=%u, size = %lu",
             (unsigned)dtag,
             (unsigned long)size);
}

/**
 * Break up data in a face's input buffer buffer into individual messages,
 * and call process_input_message on each one.
 *
 * This is used to handle things originating from the internal client -
 * its output is input for fdholder 0.
 */
static void
process_input_buffer(struct ccnr_handle *h, struct fdholder *fdholder)
{
    unsigned char *msg;
    size_t size;
    ssize_t dres;
    struct ccn_skeleton_decoder *d;

    if (fdholder == NULL || fdholder->inbuf == NULL)
        return;
    d = &fdholder->decoder;
    msg = fdholder->inbuf->buf;
    size = fdholder->inbuf->length;
    while (d->index < size) {
        dres = ccn_skeleton_decode(d, msg + d->index, size - d->index);
        if (d->state != 0)
            break;
        process_input_message(h, fdholder, msg + d->index - dres, dres, 0);
    }
    if (d->index != size) {
        ccnr_msg(h, "protocol error on fdholder %u (state %d), discarding %d bytes",
                     fdholder->filedesc, d->state, (int)(size - d->index));
        // XXX - perhaps this should be a fatal error.
    }
    fdholder->inbuf->length = 0;
    memset(d, 0, sizeof(*d));
}

/**
 * Process the input from a socket.
 *
 * The socket has been found ready for input by the poll call.
 * Decide what fdholder it corresponds to, and after checking for exceptional
 * cases, receive data, parse it into ccnb-encoded messages, and call
 * process_input_message for each one.
 */
static void
process_input(struct ccnr_handle *h, int fd)
{
    struct fdholder *fdholder = NULL;
    struct fdholder *source = NULL;
    ssize_t res;
    ssize_t dres;
    ssize_t msgstart;
    unsigned char *buf;
    struct ccn_skeleton_decoder *d;
    struct sockaddr_storage sstor;
    socklen_t addrlen = sizeof(sstor);
    struct sockaddr *addr = (struct sockaddr *)&sstor;
    int err = 0;
    socklen_t err_sz;
    
    fdholder = r_io_fdholder_from_fd(h, fd);
    if (fdholder == NULL)
        return;
    if ((fdholder->flags & (CCN_FACE_DGRAM | CCN_FACE_PASSIVE)) == CCN_FACE_PASSIVE) {
        r_io_accept_connection(h, fd);
        return;
    }
    err_sz = sizeof(err);
    res = getsockopt(fdholder->recv_fd, SOL_SOCKET, SO_ERROR, &err, &err_sz);
    if (res >= 0 && err != 0) {
        ccnr_msg(h, "error on fdholder %u: %s (%d)", fdholder->filedesc, strerror(err), err);
        if (err == ETIMEDOUT && (fdholder->flags & CCN_FACE_CONNECTING) != 0) {
            r_io_shutdown_client_fd(h, fd);
            return;
        }
    }
    d = &fdholder->decoder;
    if (fdholder->inbuf == NULL)
        fdholder->inbuf = ccn_charbuf_create();
    if (fdholder->inbuf->length == 0)
        memset(d, 0, sizeof(*d));
    buf = ccn_charbuf_reserve(fdholder->inbuf, 8800);
    memset(&sstor, 0, sizeof(sstor));
    res = recvfrom(fdholder->recv_fd, buf, fdholder->inbuf->limit - fdholder->inbuf->length,
            /* flags */ 0, addr, &addrlen);
    if (res == -1)
        ccnr_msg(h, "recvfrom fdholder %u :%s (errno = %d)",
                    fdholder->filedesc, strerror(errno), errno);
    else if (res == 0 && (fdholder->flags & CCN_FACE_DGRAM) == 0)
        r_io_shutdown_client_fd(h, fd);
    else {
        source = fdholder;
        ccnr_meter_bump(h, source->meter[FM_BYTI], res);
        source->recvcount++;
        source->surplus = 0; // XXX - we don't actually use this, except for some obscure messages.
        if (res <= 1 && (source->flags & CCN_FACE_DGRAM) != 0) {
            // XXX - If the initial heartbeat gets missed, we don't realize the locality of the fdholder.
            if (h->debug & 128)
                ccnr_msg(h, "%d-byte heartbeat on %d", (int)res, source->filedesc);
            return;
        }
        fdholder->inbuf->length += res;
        msgstart = 0;
        if (((fdholder->flags & CCN_FACE_UNDECIDED) != 0 &&
             fdholder->inbuf->length >= 6 &&
             0 == memcmp(fdholder->inbuf->buf, "GET ", 4))) {
            ccnr_stats_handle_http_connection(h, fdholder);
            return;
        }
        dres = ccn_skeleton_decode(d, buf, res);
        while (d->state == 0) {
            process_input_message(h, source,
                                  fdholder->inbuf->buf + msgstart,
                                  d->index - msgstart,
                                  (fdholder->flags & CCN_FACE_LOCAL) != 0);
            msgstart = d->index;
            if (msgstart == fdholder->inbuf->length) {
                fdholder->inbuf->length = 0;
                return;
            }
            dres = ccn_skeleton_decode(d,
                    fdholder->inbuf->buf + d->index, // XXX - msgstart and d->index are the same here - use msgstart
                    res = fdholder->inbuf->length - d->index);  // XXX - why is res set here?
        }
        if ((fdholder->flags & CCN_FACE_DGRAM) != 0) {
            ccnr_msg(h, "protocol error on fdholder %u, discarding %u bytes",
                source->filedesc,
                (unsigned)(fdholder->inbuf->length));  // XXX - Should be fdholder->inbuf->length - d->index (or msgstart)
            fdholder->inbuf->length = 0;
            /* XXX - should probably ignore this source for a while */
            return;
        }
        else if (d->state < 0) {
            ccnr_msg(h, "protocol error on fdholder %u", source->filedesc);
            r_io_shutdown_client_fd(h, fd);
            return;
        }
        if (msgstart < fdholder->inbuf->length && msgstart > 0) {
            /* move partial message to start of buffer */
            memmove(fdholder->inbuf->buf, fdholder->inbuf->buf + msgstart,
                fdholder->inbuf->length - msgstart);
            fdholder->inbuf->length -= msgstart;
            d->index -= msgstart;
        }
    }
}

PUBLIC void
r_dispatch_process_internal_client_buffer(struct ccnr_handle *h)
{
    struct fdholder *fdholder = h->face0;
    if (fdholder == NULL)
        return;
    fdholder->inbuf = ccn_grab_buffered_output(h->internal_client);
    if (fdholder->inbuf == NULL)
        return;
    ccnr_meter_bump(h, fdholder->meter[FM_BYTI], fdholder->inbuf->length);
    process_input_buffer(h, fdholder);
    ccn_charbuf_destroy(&(fdholder->inbuf));
}
/**
 * Run the main loop of the ccnr
 */
PUBLIC void
r_dispatch_run(struct ccnr_handle *h)
{
    int i;
    int res;
    int timeout_ms = -1;
    int prev_timeout_ms = -1;
    int usec;
    for (h->running = 1; h->running;) {
        r_dispatch_process_internal_client_buffer(h);
        usec = ccn_schedule_run(h->sched);
        timeout_ms = (usec < 0) ? -1 : ((usec + 960) / 1000);
        if (timeout_ms == 0 && prev_timeout_ms == 0)
            timeout_ms = 1;
        r_dispatch_process_internal_client_buffer(h);
        r_io_prepare_poll_fds(h);
        if (0) ccnr_msg(h, "at ccnr.c:%d poll(h->fds, %d, %d)", __LINE__, h->nfds, timeout_ms);
        res = poll(h->fds, h->nfds, timeout_ms);
        prev_timeout_ms = ((res == 0) ? timeout_ms : 1);
        if (-1 == res) {
            ccnr_msg(h, "poll: %s (errno = %d)", strerror(errno), errno);
            sleep(1);
            continue;
        }
        for (i = 0; res > 0 && i < h->nfds; i++) {
            if (h->fds[i].revents != 0) {
                res--;
                if (h->fds[i].revents & (POLLERR | POLLNVAL | POLLHUP)) {
                    if (h->fds[i].revents & (POLLIN))
                        process_input(h, h->fds[i].fd);
                    else
                        r_io_shutdown_client_fd(h, h->fds[i].fd);
                    continue;
                }
                if (h->fds[i].revents & (POLLOUT))
                    r_link_do_deferred_write(h, h->fds[i].fd);
                else if (h->fds[i].revents & (POLLIN))
                    process_input(h, h->fds[i].fd);
            }
        }
    }
}
