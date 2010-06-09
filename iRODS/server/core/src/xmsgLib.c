/*** Copyright (c), The Regents of the University of California            ***
 *** For more information please refer to files in the COPYRIGHT directory ***/

/* xmsgLib.c - library routines for irodsXmsg
 */

#ifndef windows_platform
#include <pthread.h>
#endif
#include "xmsgLib.h"
#include "rsApiHandler.h"

#ifndef windows_platform
pthread_mutex_t ReqQueCondMutex;
pthread_cond_t ReqQueCond;
pthread_cond_t ReqQueCond;
pthread_t ProcReqThread[NUM_XMSG_THR];
#endif

xmsgReq_t *XmsgReqHead = NULL;
ticketHashQue_t XmsgHashQue[NUM_HASH_SLOT];
xmsgQue_t XmsgQue;

int 
initThreadEnv ()
{
#ifndef windows_platform
    pthread_mutex_init (&ReqQueCondMutex, NULL);
    pthread_cond_init (&ReqQueCond, NULL);
#endif

    return (0);
}

int
addXmsgToXmsgQue (irodsXmsg_t *xmsg, xmsgQue_t *xmsgQue)
{

    if (xmsg == NULL || xmsgQue == NULL) {
        rodsLog (LOG_ERROR,
          "addXmsgToQue: input xmsg or xmsgQue is NULL");
        return (SYS_INTERNAL_NULL_INPUT_ERR);
    }

    xmsg->next = xmsg->prev = NULL;

    if (xmsgQue->head == NULL) {
	xmsgQue->head = xmsgQue->tail = xmsg;
    } else {
	/* que it on top */
	xmsgQue->head->prev = xmsg;
	xmsg->next = xmsgQue->head;
	xmsgQue->head = xmsg;
    }

    return (0);
}

int
rmXmsgFromXmsgQue (irodsXmsg_t *xmsg, xmsgQue_t *xmsgQue)
{
    if (xmsg == NULL || xmsgQue == NULL) {
        rodsLog (LOG_ERROR,
          "addXmsgToQue: input xmsg or xmsgQue is NULL");
        return (SYS_INTERNAL_NULL_INPUT_ERR);
    }

    if (xmsg->prev == NULL) {
	/* at head */
	xmsgQue->head = xmsg->next;
    } else {
	xmsg->prev->next = xmsg->next;
    }

    if (xmsg->next == NULL) {
	/* at tail */
        xmsgQue->tail = xmsg->prev;
    } else {
	xmsg->next->prev = xmsg->prev;
    }
 
    xmsg->prev = xmsg->next = NULL;

    return (0);
}

int
rmXmsgFromXmsgTcketQue (irodsXmsg_t *xmsg, xmsgQue_t *xmsgQue)
{
    if (xmsg == NULL || xmsgQue == NULL) {
        rodsLog (LOG_ERROR,
          "addXmsgToQue: input xmsg or xmsgQue is NULL");
        return (SYS_INTERNAL_NULL_INPUT_ERR);
    }

    if (xmsg->tprev == NULL) {
	/* at head */
	xmsgQue->head = xmsg->tnext;
    } else {
	xmsg->tprev->tnext = xmsg->tnext;
    }

    if (xmsg->tnext == NULL) {
	/* at tail */
        xmsgQue->tail = xmsg->tprev;
    } else {
	xmsg->tnext->tprev = xmsg->tprev;
    }
 
    xmsg->tprev = xmsg->tnext = NULL;

    return (0);
}

int
addXmsgToTicketMsgStruct (irodsXmsg_t *xmsg, 
ticketMsgStruct_t *ticketMsgStruct)
{
    if (xmsg == NULL || ticketMsgStruct == NULL) {
        rodsLog (LOG_ERROR,
          "addXmsgToTicketMsgStruct: input xmsg or ticketMsgStruct is NULL");
        return (SYS_INTERNAL_NULL_INPUT_ERR);
    }

    /* up the expire time */
    if (xmsg->sendTime + INC_EXPIRE_INT > ticketMsgStruct->ticket.expireTime) {
	ticketMsgStruct->ticket.expireTime = xmsg->sendTime + INC_EXPIRE_INT;
    }

    if (ticketMsgStruct->xmsgQue.head == NULL) {
	ticketMsgStruct->xmsgQue.head = ticketMsgStruct->xmsgQue.tail = xmsg;
	xmsg->tnext = xmsg->tprev = NULL;
    } else {
	/* link it to the end */
	ticketMsgStruct->xmsgQue.tail->tnext = xmsg;
	xmsg->tprev = ticketMsgStruct->xmsgQue.tail;
	ticketMsgStruct->xmsgQue.tail = xmsg;
	xmsg->tnext = NULL;
    }
    xmsg->ticketMsgStruct = ticketMsgStruct;
    xmsg->seqNumber = ticketMsgStruct->nxtSeqNumber;
    ticketMsgStruct->nxtSeqNumber =  ticketMsgStruct->nxtSeqNumber + 1;
    rodsLog (LOG_ERROR,
	     "SEQNum: %i", ticketMsgStruct->nxtSeqNumber );

    return (0);
}

int 
getIrodsXmsgByMsgNum (int rcvTicket, int msgNumber, 
irodsXmsg_t **outIrodsXmsg) 
{
    int status;
    irodsXmsg_t *tmpIrodsXmsg;
    ticketMsgStruct_t *ticketMsgStruct;

    if (outIrodsXmsg == NULL) {
        rodsLog (LOG_ERROR,
          "getIrodsXmsgByMsgNum: input outIrodsXmsg is NULL");
        return (SYS_INTERNAL_NULL_INPUT_ERR);
    }

    /* locate the ticketMsgStruct_t */

    status = getTicketMsgStructByTicket (rcvTicket, &ticketMsgStruct);

    if (status < 0) {
        return status;
    }

    /* now locate the irodsXmsg_t */

    tmpIrodsXmsg = ticketMsgStruct->xmsgQue.head;

    if (msgNumber != ANY_MSG_NUMBER) {
        while (tmpIrodsXmsg != NULL) {
	    if (tmpIrodsXmsg->sendXmsgInfo->msgNumber == msgNumber) break;
	    tmpIrodsXmsg = tmpIrodsXmsg->tnext;
	}
    }
    *outIrodsXmsg = tmpIrodsXmsg;
    if (tmpIrodsXmsg == NULL) {
        return SYS_NO_XMSG_FOR_MSG_NUMBER;
    } else {
	return 0;
    }
}

int
addTicketToHQue (xmsgTicketInfo_t *ticket, ticketHashQue_t *ticketHQue)
{
    int status;

    ticketMsgStruct_t *tmpTicketMsgStruct;

    if (ticket == NULL || ticketHQue == NULL) {
        rodsLog (LOG_ERROR,
          "addTicketToHQue: input ticket or ticketHQue is NULL");
        return (SYS_INTERNAL_NULL_INPUT_ERR);
    }

    tmpTicketMsgStruct = calloc (1, sizeof (ticketMsgStruct_t));

    /* copy the content of the ticket */

    tmpTicketMsgStruct->ticket = *ticket;
    status = addTicketMsgStructToHQue (tmpTicketMsgStruct, ticketHQue);

    if (status < 0) {
	free (tmpTicketMsgStruct);
    }

    return (status);
}

int
addTicketMsgStructToHQue (ticketMsgStruct_t *ticketMsgStruct, 
ticketHashQue_t *ticketHQue)
{
    ticketMsgStruct_t *tmpTicketMsgStruct;

    if (ticketMsgStruct == NULL || ticketHQue == NULL) {
        rodsLog (LOG_ERROR,
          "addTicketMsgStructToHQue: ticketMsgStruct or ticketHQue is NULL");
        return (SYS_INTERNAL_NULL_INPUT_ERR);
    }

    ticketMsgStruct->hnext = ticketMsgStruct->hprev = NULL;
    ticketMsgStruct->nxtSeqNumber = 0;
    ticketMsgStruct->ticketHashQue = ticketHQue;

    if (ticketHQue->head == NULL) {
	ticketHQue->head = ticketHQue->tail = ticketMsgStruct;
	return (0);
    }

    
    /* que in decending order of rcvTicket */
    tmpTicketMsgStruct = ticketHQue->head;
    while (tmpTicketMsgStruct != NULL) {
	if (ticketMsgStruct->ticket.rcvTicket == 
	  tmpTicketMsgStruct->ticket.rcvTicket) {
	    return (SYS_DUPLICATE_XMSG_TICKET);
	} else if (ticketMsgStruct->ticket.rcvTicket > 
	    tmpTicketMsgStruct->ticket.rcvTicket) {
	    break;
	} else {
	    tmpTicketMsgStruct = tmpTicketMsgStruct->hnext;
	}
    }
    if (tmpTicketMsgStruct == NULL) {
	/* reached the end */
	ticketHQue->tail->hnext = ticketMsgStruct;
	ticketMsgStruct->hprev = ticketHQue->tail;
	ticketHQue->tail = ticketMsgStruct;
    } else if (tmpTicketMsgStruct == ticketHQue->head) {
	/* need to put ticketMsgStruct at the head */
	ticketHQue->head->hprev = ticketMsgStruct;
	ticketMsgStruct->hnext = ticketHQue->head;
	ticketHQue->head = ticketMsgStruct;
    } else {
	/* in the middle */
	ticketMsgStruct->hprev = tmpTicketMsgStruct->hprev;
	ticketMsgStruct->hnext = tmpTicketMsgStruct;
	tmpTicketMsgStruct->hprev->hnext = ticketMsgStruct;
	tmpTicketMsgStruct->hprev = tmpTicketMsgStruct;
    }
	
    return (0);
}

int
rmTicketMsgStructFromHQue (ticketMsgStruct_t *ticketMsgStruct, 
ticketHashQue_t *ticketHQue)
{
    if (ticketMsgStruct == NULL || ticketHQue == NULL) {
        rodsLog (LOG_ERROR,
          "rmTicketMsgStructFromHQue: ticketMsgStruct or ticketHQue is NULL");
        return (SYS_INTERNAL_NULL_INPUT_ERR);
    }

    if (ticketMsgStruct->hprev == NULL) {
	/* at head */
	ticketHQue->head = ticketMsgStruct->hnext;
    } else {
	ticketMsgStruct->hprev->hnext = ticketMsgStruct->hnext;
    }

    if (ticketMsgStruct->hnext == NULL) {
	/* at tail */
        ticketHQue->tail = ticketMsgStruct->hprev;
    } else {
	ticketMsgStruct->hnext->hprev = ticketMsgStruct->hprev;
    }
 
    ticketMsgStruct->hprev = ticketMsgStruct->hnext = NULL;

    return (0);
}

/* add incoming request to the bottom of the link list */ 

int
addReqToQue (int sock)
{
    xmsgReq_t *myXmsgReq, *tmpXmsgReq;

#ifndef windows_platform
    pthread_mutex_lock (&ReqQueCondMutex);
#endif
    myXmsgReq = calloc (1, sizeof (xmsgReq_t));

    myXmsgReq->sock = sock;

    if (XmsgReqHead == NULL) {
	XmsgReqHead = myXmsgReq;
    } else {
        tmpXmsgReq = XmsgReqHead;
	while (tmpXmsgReq->next != NULL) {
	    tmpXmsgReq = tmpXmsgReq->next;
	}
	tmpXmsgReq->next = myXmsgReq;
    }

#ifndef windows_platform
    pthread_cond_signal (&ReqQueCond);
    pthread_mutex_unlock (&ReqQueCondMutex);
#endif

    return (0);
}

xmsgReq_t *
getReqFromQue ()
{
    xmsgReq_t *myXmsgReq = NULL;

    while (myXmsgReq == NULL) {
#ifndef windows_platform
        pthread_mutex_lock (&ReqQueCondMutex);
#endif
        if (XmsgReqHead != NULL) {
            myXmsgReq = XmsgReqHead;
            XmsgReqHead = XmsgReqHead->next;
#ifndef windows_platform
            pthread_mutex_unlock (&ReqQueCondMutex);
#endif
            break;
	}

#ifndef windows_platform
	pthread_cond_wait (&ReqQueCond, &ReqQueCondMutex);
#endif
        if (XmsgReqHead == NULL) {
#ifndef windows_platform
	    pthread_mutex_unlock (&ReqQueCondMutex);
#endif
	    continue;
	} else {
            myXmsgReq = XmsgReqHead;
    	    XmsgReqHead = XmsgReqHead->next;
#ifndef windows_platform
	    pthread_mutex_unlock (&ReqQueCondMutex);
#endif
	    break;
	}
    }

    return (myXmsgReq);
}

int
startXmsgThreads ()
{
    int status = 0;
#ifndef windows_platform
    int i;
    for (i = 0; i < NUM_XMSG_THR; i++) {
        status = pthread_create(&ProcReqThread[i], NULL, 
          (void *(*)(void *)) procReqRoutine, (void *) NULL);
    }
#endif

    return (status);
}

void
procReqRoutine ()
{
    xmsgReq_t *myXmsgReq = NULL;
    startupPack_t *startupPack;
    rsComm_t rsComm;
    int status;
    fd_set sockMask;
    struct timeval msgTimeout;

    while (1) {
	myXmsgReq = getReqFromQue ();
	if (myXmsgReq == NULL) {
	    /* someone else took care of it */
	    continue;
	}

        status = readStartupPack (myXmsgReq->sock, &startupPack, NULL);
	if (status < 0) {
            rodsLog (LOG_ERROR,
              "procReqRoutine: readStartupPack error, status = %d", status);
	    free (myXmsgReq);
            continue;
	}
	memset (&rsComm, 0, sizeof (rsComm));
	initRsCommWithStartupPack (&rsComm, startupPack);
	rsComm.sock = myXmsgReq->sock;
        status = sendVersion (rsComm.sock, 0, 0, NULL, 0);

        if (status < 0) {
            sendVersion (rsComm.sock, SYS_AGENT_INIT_ERR, 0, NULL, 0);
	    free (myXmsgReq);
            continue;
        }
        FD_ZERO(&sockMask);
	memset (&msgTimeout, 0, sizeof (msgTimeout));
	msgTimeout.tv_sec = REQ_MSG_TIMEOUT_TIME;
	while (1) {
	    int numSock;

    	    FD_SET (rsComm.sock, &sockMask);
            while ((numSock = select (rsComm.sock + 1, &sockMask,
              (fd_set *) NULL, (fd_set *) NULL, &msgTimeout)) < 0) {
                if (errno == EINTR) {
                    rodsLog (LOG_NOTICE, 
		      "procReqRoutine: select() interrupted");
                    FD_SET(rsComm.sock, &sockMask);
                    continue;
                } else {
		    break;	/* timedout or something */
                }
	    }
	    if (numSock < 0) break;
            status = readAndProcClientMsg (&rsComm, 0);
	    if (status < 0) break;
        }
	close (rsComm.sock);
	free (myXmsgReq);
    }
}

/* The hash function which use rcvTicket as the key. It take the modulo of
 * rcvTicket/NUM_HASH_SLOT
 */

int
ticketHashFunc (uint rcvTicket)
{
    int mySlot = rcvTicket % NUM_HASH_SLOT;

    return (mySlot); 
}

int
initXmsgHashQue ()
{

  xmsgTicketInfo_t *outXmsgTicketInfo;
  time_t thisTime;
  int hashSlotNum;
 
    memset (XmsgHashQue, 0, NUM_HASH_SLOT * sizeof (ticketHashQue_t));
    memset (&XmsgQue, 0, sizeof (XmsgQue));

    /*** added by Raja on 5/12/2010 to have a permanent message queue with ticket-id =1,2,3***/

    thisTime = time (NULL);

    outXmsgTicketInfo = calloc (1, sizeof (xmsgTicketInfo_t));
    outXmsgTicketInfo->expireTime = thisTime + (MAX_EXPIRE_INT * 500);
    outXmsgTicketInfo->rcvTicket = 1;
    outXmsgTicketInfo->sendTicket = 1;
    outXmsgTicketInfo->flag = 1;
    hashSlotNum = ticketHashFunc (outXmsgTicketInfo->rcvTicket);
    addTicketToHQue (outXmsgTicketInfo, &XmsgHashQue[hashSlotNum]);
    
    outXmsgTicketInfo = calloc (1, sizeof (xmsgTicketInfo_t));
    outXmsgTicketInfo->expireTime = thisTime + (MAX_EXPIRE_INT * 500);
    outXmsgTicketInfo->rcvTicket = 2;
    outXmsgTicketInfo->sendTicket = 2;
    outXmsgTicketInfo->flag = 1;
    hashSlotNum = ticketHashFunc (outXmsgTicketInfo->rcvTicket);
    addTicketToHQue (outXmsgTicketInfo, &XmsgHashQue[hashSlotNum]);

    outXmsgTicketInfo = calloc (1, sizeof (xmsgTicketInfo_t));
    outXmsgTicketInfo->expireTime = thisTime + (MAX_EXPIRE_INT * 500);
    outXmsgTicketInfo->rcvTicket = 3;
    outXmsgTicketInfo->sendTicket = 3;
    outXmsgTicketInfo->flag = 1;
    hashSlotNum = ticketHashFunc (outXmsgTicketInfo->rcvTicket);
    addTicketToHQue (outXmsgTicketInfo, &XmsgHashQue[hashSlotNum]);


    /*** added by Raja on 5/12/2010 to have a permanent message queue with ticket-id = 1,2,3***/

    return (0);
}

int
getTicketMsgStructByTicket (uint rcvTicket, 
ticketMsgStruct_t **outTicketMsgStruct)
{
    int hashSlotNum;
    ticketMsgStruct_t *tmpTicketMsgStruct;

    hashSlotNum = ticketHashFunc (rcvTicket);

    tmpTicketMsgStruct = XmsgHashQue[hashSlotNum].head;

    while (tmpTicketMsgStruct != NULL) {
	if (rcvTicket == tmpTicketMsgStruct->ticket.rcvTicket) {
	    *outTicketMsgStruct = tmpTicketMsgStruct;
	    return 0;
	} else if (rcvTicket > tmpTicketMsgStruct->ticket.rcvTicket) {
	    *outTicketMsgStruct = NULL;
	    return SYS_UNMATCHED_XMSG_TICKET;
	} else {
	    tmpTicketMsgStruct = tmpTicketMsgStruct->hnext;
	}
    }

    /* no match */
    *outTicketMsgStruct = NULL;
    return SYS_UNMATCHED_XMSG_TICKET;
}

int
_rsRcvXmsg (irodsXmsg_t *irodsXmsg, rcvXmsgOut_t *rcvXmsgOut)
{
    sendXmsgInfo_t *sendXmsgInfo;
    ticketMsgStruct_t *ticketMsgStruct;

    if (irodsXmsg == NULL || rcvXmsgOut == NULL) {
        rodsLog (LOG_ERROR,
          "_rsRcvXmsg: input irodsXmsg or rcvXmsgOut is NULL");
        return (SYS_INTERNAL_NULL_INPUT_ERR);
    }
    sendXmsgInfo = irodsXmsg->sendXmsgInfo;
    ticketMsgStruct = irodsXmsg->ticketMsgStruct;

    sendXmsgInfo->numRcv--;

    if (sendXmsgInfo->numRcv <= 0 && sendXmsgInfo->numDeli <= 0) {
	/* done with this msg */
	rmXmsgFromXmsgQue (irodsXmsg, &XmsgQue);
	rmXmsgFromXmsgTcketQue (irodsXmsg, &ticketMsgStruct->xmsgQue);
	rcvXmsgOut->msg = sendXmsgInfo->msg;
	rcvXmsgOut->seqNumber  = irodsXmsg->seqNumber;
	rcvXmsgOut->msgNumber  = sendXmsgInfo->msgNumber;
	sendXmsgInfo->msg = NULL;
	rstrcpy (rcvXmsgOut->msgType, sendXmsgInfo->msgType, HEADER_TYPE_LEN);
	rstrcpy (rcvXmsgOut->sendUserName, irodsXmsg->sendUserName,
	  NAME_LEN);
	clearSendXmsgInfo (sendXmsgInfo);
	free (irodsXmsg);
	/* take out the ticket too ? */
	if (ticketMsgStruct->xmsgQue.head == NULL &&
	  (!(ticketMsgStruct->ticket.flag & MULTI_MSG_TICKET) || 
	  time (NULL) >= ticketMsgStruct->ticket.expireTime)) {
	    rmTicketMsgStructFromHQue (ticketMsgStruct, 
	      (ticketHashQue_t *) ticketMsgStruct->ticketHashQue);
	}
    } else {
	rcvXmsgOut->msg = strdup (sendXmsgInfo->msg);
	rcvXmsgOut->seqNumber  = irodsXmsg->seqNumber;
	rcvXmsgOut->msgNumber  = sendXmsgInfo->msgNumber;
        rstrcpy (rcvXmsgOut->msgType, sendXmsgInfo->msgType, HEADER_TYPE_LEN);
        rstrcpy (rcvXmsgOut->sendUserName, irodsXmsg->sendUserName,
          NAME_LEN);
    }
    return (0);
}

