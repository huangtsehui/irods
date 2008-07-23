#include "rsApiHandler.h"
#include "integrityChecksMS.h"

const char collectionname[]="/tempZone/home/rods";


int msiCheckFilesizeRange (msParam_t *mPin1, msParam_t *mPin2, msParam_t *mPin3, msParam_t *mPout1, ruleExecInfo_t *rei) {

	genQueryInp_t genQueryInp;
	genQueryOut_t *genQueryOut;
	char condStr[MAX_NAME_LEN];
	rsComm_t *rsComm;
	char collname[200];
	int maxfilesize, minfilesize;
	bytesBuf_t	*mybuf;
	// int i;
	int j;

	RE_TEST_MACRO ("    Calling msiCheckCollectionFilesizeRange")

	/* Sanity check */
	if (rei == NULL || rei->rsComm == NULL) {
		rodsLog (LOG_ERROR, "msiCheckFilesizeRange: input rei or rsComm is NULL");
		return (SYS_INTERNAL_NULL_INPUT_ERR);
	}

	rsComm = rei->rsComm;

	/* construct an SQL query from the parameter list */
	strcpy (collname,  (char*) mPin1->inOutStruct);
	maxfilesize  = (int) atoi(mPin2->inOutStruct);
	minfilesize  = (int) atoi(mPin3->inOutStruct);

	rodsLog (LOG_ERROR, "-1 s tuff: ");

	memset (&genQueryInp, 0, sizeof (genQueryInp_t));
	genQueryInp.maxRows = MAX_SQL_ROWS;

	/* this is the info we want returned from the query */
	addInxIval (&genQueryInp.selectInp, COL_DATA_NAME, 1);
	addInxIval (&genQueryInp.selectInp, COL_DATA_SIZE, 1);
	addInxIval (&genQueryInp.selectInp, COL_COLL_NAME, 1);

	/* make the condition */

	rodsLog (LOG_ERROR, "0 stuff: ");

	snprintf (condStr, MAX_NAME_LEN, " = '%s'", collectionname);
	addInxVal (&genQueryInp.sqlCondInp, COL_COLL_NAME, condStr); /* what's this? */
	rodsLog (LOG_ERROR, "1 s tuff: ");

	//j = rsGenQuery (rsComm, &genQueryInp, genQueryOut);
	rodsLog (LOG_ERROR, "3 s tuff: ");

	j=0;
	if (j != CAT_NO_ROWS_FOUND) {

		bytesBuf_t	*buf_p;
		buf_p = mybuf;

	rodsLog (LOG_ERROR, "4 s tuff: ");
		mybuf->buf = (void*) malloc (1000);
		strncpy (mybuf->buf, "butter", strlen("butter"));

	rodsLog (LOG_ERROR, "5 s tuff: ");
		/* we got results - do something cool */
/*
		for (i=0; i<genQueryOut->totalRowCount; i++) {
			memcpy (buf_p->buf, genQueryOut->sqlResult[i], sizeof (sqlResult_t));
			buf_p += sizeof (sqlResult_t);
		}
*/
	}

/*
	printGenQI(genQueryInp);  
	printGenQI(genQueryOut);  
*/

	/* OK now copy over the results of the query....maybe */


	rodsLog (LOG_ERROR, "6 s tuff: ");
	/* fill in the return value struct */
	fillStrInMsParam (mPout1, "some stuff");
	rodsLog (LOG_ERROR, "7 s tuff: ");
  
//	return(rei->status);
	return (0);

}


int msiHiThere (msParam_t *mPin1, msParam_t *mPin2, msParam_t *mPout1, msParam_t *mPout2, ruleExecInfo_t *rei) {

  char *in1,  out1;
  int i, in2, out2;
   
  RE_TEST_MACRO ("    Calling susan's hithere")
  /* the above line is needed for loop back testing using irule -i option */
  
/*
  in1  = (char *) mPin1->inOutStruct;
  in2  = (int)    mPin2->inOutStruct;
  out1 = (char *) mPout1->inOutStruct;
  out2 = (int)    mPout2->inOutStruct;
*/
  
  i = hithere (in1, in2, out1, &out2);

	fillIntInMsParam (mPout2, i);

/*  mPout2->inOutStruct = (int) i; */


  
  return(i);
}

