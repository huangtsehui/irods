/*** Copyright (c), The Regents of the University of California            ***
 *** For more information please refer to files in the COPYRIGHT directory ***/
/* This is script-generated code (for the most part).  */ 
/* See generalRowInsert.h for a description of this API call.*/

#include "generalRowInsert.h"
#include "reGlobalsExtern.h"
#include "icatHighLevelRoutines.h"

int
rsGeneralRowInsert (rsComm_t *rsComm, generalRowInsertInp_t *generalRowInsertInp )
{
    rodsServerHost_t *rodsServerHost;
    int status;

    rodsLog(LOG_DEBUG, "generalRowInsert");

    status = getAndConnRcatHost(rsComm, MASTER_RCAT, NULL, &rodsServerHost);
    if (status < 0) {
       return(status);
    }

    if (rodsServerHost->localFlag == LOCAL_HOST) {
#ifdef RODS_CAT
       status = _rsGeneralRowInsert (rsComm, generalRowInsertInp);
#else
       status = SYS_NO_RCAT_SERVER_ERR;
#endif
    }
    else {
       status = rcGeneralRowInsert(rodsServerHost->conn,
			       generalRowInsertInp);
    }

    if (status < 0) { 
       rodsLog (LOG_NOTICE,
		"rsGeneralRowInsert: rcGeneralRowInsert failed");
    }
    return (status);
}

#ifdef RODS_CAT
int
_rsGeneralRowInsert(rsComm_t *rsComm, generalRowInsertInp_t *generalRowInsertInp )
{
    int status;

    rodsLog (LOG_DEBUG,
	     "_rsGeneralRowInsert tableName=%s", 
	     generalRowInsertInp->tableName);

    if (strcmp(generalRowInsertInp->tableName,"serverload")==0) {
       status = chlRegServerLoad(rsComm, 
				 generalRowInsertInp->arg1,
				 generalRowInsertInp->arg2,
				 generalRowInsertInp->arg3,
				 generalRowInsertInp->arg4,
				 generalRowInsertInp->arg5,
				 generalRowInsertInp->arg6,
				 generalRowInsertInp->arg7,
				 generalRowInsertInp->arg8,
				 generalRowInsertInp->arg9);
       return(status);
    }
    if (strcmp(generalRowInsertInp->tableName,"serverloaddigest")==0) {
       status = chlRegServerLoadDigest(rsComm, 
				 generalRowInsertInp->arg1,
				 generalRowInsertInp->arg2);
       return(status);
    }
    return(CAT_INVALID_ARGUMENT);
} 
#endif
