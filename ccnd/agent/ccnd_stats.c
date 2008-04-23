/*
 * ccnd_stats.c
 *  
 * Copyright 2008 Palo Alto Research Center, Inc. All rights reserved.
 * $Id$
 */

#include <errno.h>
#include <fcntl.h>
#include <netdb.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <unistd.h>

#include <ccn/charbuf.h>
#include <ccn/indexbuf.h>
#include <ccn/schedule.h>
#include <ccn/hashtb.h>

#include "ccnd_private.h"

struct ccnd_stats {
    long total_interest_counts;
};

int
ccnd_collect_stats(struct ccnd *h, struct ccnd_stats *ans)
{
    struct hashtb_enumerator ee;
    struct hashtb_enumerator *e = &ee;
    struct interest_entry *interest;
    long total_interest = 0;
    int i;
    int n;
    for(hashtb_start(h->interest_tab, e); e->data != NULL; hashtb_next(e)) {
        interest = e->data;
        n = interest->counters->n;
        for (i = 0; i < n; i++)
            total_interest += interest->counters->buf[i];
    }
    hashtb_end(e);
    ans->total_interest_counts = (total_interest + CCN_UNIT_INTEREST-1) / CCN_UNIT_INTEREST;
    return(0);
}

static char *
collect_stats_html(struct ccnd *h)
{
    char *ans;
    struct ccnd_stats stats = {0};
    struct ccn_charbuf *b = ccn_charbuf_create();
    
    ccnd_collect_stats(h, &stats);
    ccn_charbuf_putf(b,
        "HTTP/0.9 200 OK\r\n"
        "Content-Type: text/html; charset=iso-8859-1\r\n\r\n"
        "<html>"
        "<title>ccnd[%d]</title>"
        "<meta http-equiv='refresh' content='3'>"
        "<body>"
        "<div><b>Content items in store:</b> %d</div>"
        "<div><b>Interests:</b> %d names, %ld pending, %d propagating</div>"
        "<div><b>Active faces and listeners:</b> %d</div>"
        "</body>"
        "<html>",
        getpid(),
        hashtb_n(h->content_tab),
        hashtb_n(h->interest_tab), stats.total_interest_counts, hashtb_n(h->propagating_tab),
        hashtb_n(h->faces_by_fd) + hashtb_n(h->dgram_faces));
    ans = strdup((char *)b->buf);
    ccn_charbuf_destroy(&b);
    return(ans);
}

static const char *resp404 = "HTTP/0.9 404 Not Found\r\n";
static int
check_for_http_connection(struct ccn_schedule *sched,
    void *clienth,
    struct ccn_scheduled_event *ev,
    int flags)
{
    int res;
    int sock;
    char *response = NULL;
    sock = ev->evint;
    if ((flags && CCN_SCHEDULE_CANCEL) != 0) {
        close(sock);
        return(0);
    }
    for (;;) {
        char buf[7] = "GET / ";
        int fd = accept(sock, NULL, 0);
        if (fd == -1)
            break;
        fcntl(fd, F_SETFL, O_NONBLOCK);
        res = read(fd, buf, sizeof(buf)-1);
        if ((res == -1 && errno == EAGAIN) || res == sizeof(buf)-1) {
            if (0 == strcmp(buf, "GET / ")) {
                if (response == NULL)
                    response = collect_stats_html(clienth);
                write(fd, response, strlen(response));
            }
            else
                write(fd, resp404, strlen(resp404));
        }
        close(fd);
    }
    free(response);
    return(4000000);
}

int
ccnd_stats_httpd_start(struct ccnd *h)
{
    int res;
    int sock;
    int yes = 1;
    struct addrinfo hints = {0};
    struct addrinfo *ai = NULL;
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;
    hints.ai_flags = AI_PASSIVE;
    res = getaddrinfo(NULL, "8544", &hints, &ai);
    if (res == -1) {
        perror("ccnd_stats_httpd_listen: getaddrinfo");
        return(-1);
    }
    sock = socket(ai->ai_family, SOCK_STREAM, 0);
    if (sock == -1) {
        perror("ccnd_stats_httpd_listen: getaddrinfo");
        return(-1);
    }
    setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes));
    res = bind(sock, ai->ai_addr, ai->ai_addrlen);
    if (res == -1) {
        perror("ccnd_stats_httpd_listen: bind");
        close(sock);
        return(-1);
    }
    res = fcntl(sock, F_SETFL, O_NONBLOCK);
    if (res == -1) {
        perror("ccnd_stats_httpd_listen: fcntl");
        close(sock);
        return(-1);
    }
    res = listen(sock, 30);
    if (res == -1) {
        perror("ccnd_stats_httpd_listen: listen");
        close(sock);
        return(-1);
    }
    freeaddrinfo(ai);
    ai = NULL;
    ccn_schedule_event(h->sched, 1000000, &check_for_http_connection, NULL, sock);
    return(0);
}
