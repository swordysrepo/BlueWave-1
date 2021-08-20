ALTER TABLE APPLICATION.USER ADD COLUMN IF NOT EXISTS INFO jsonb;




CREATE TABLE APPLICATION.DASHBOARD_USER (
    ID BIGSERIAL NOT NULL,
    USER_ID bigint NOT NULL,
    DASHBOARD_ID bigint NOT NULL,
    READ_ONLY boolean NOT NULL DEFAULT true,
    CONSTRAINT PK_DASHBOARD_USER PRIMARY KEY (ID)
);


CREATE TABLE APPLICATION.DASHBOARD_GROUP (
    ID BIGSERIAL NOT NULL,
    NAME text NOT NULL,
    DESCRIPTION text,
    USER_ID bigint NOT NULL,
    INFO jsonb,
    CONSTRAINT PK_DASHBOARD_GROUP PRIMARY KEY (ID)
);


CREATE TABLE APPLICATION.DASHBOARD_GROUP_DASHBOARD (
    DASHBOARD_GROUP_ID BIGINT NOT NULL,
    DASHBOARD_ID BIGINT NOT NULL,
    CONSTRAINT FK_DASHBOARD_GROUP_DASHBOARD FOREIGN KEY (DASHBOARD_GROUP_ID) REFERENCES APPLICATION.DASHBOARD_GROUP(ID)
        ON DELETE CASCADE ON UPDATE NO ACTION
);

ALTER TABLE APPLICATION.DASHBOARD_GROUP_DASHBOARD ADD FOREIGN KEY (DASHBOARD_ID) REFERENCES APPLICATION.DASHBOARD(ID)
    ON DELETE CASCADE ON UPDATE NO ACTION;

CREATE INDEX IDX_DASHBOARD_GROUP_DASHBOARD_DASHBOARD_GROUP_ID ON APPLICATION.DASHBOARD_GROUP_DASHBOARD(DASHBOARD_GROUP_ID);
CREATE INDEX IDX_DASHBOARD_GROUP_DASHBOARD_DASHBOARD_ID ON APPLICATION.DASHBOARD_GROUP_DASHBOARD(DASHBOARD_ID);


ALTER TABLE APPLICATION.DASHBOARD_USER ADD FOREIGN KEY (USER_ID) REFERENCES APPLICATION.USER(ID)
    ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE APPLICATION.DASHBOARD_USER ADD FOREIGN KEY (DASHBOARD_ID) REFERENCES APPLICATION.DASHBOARD(ID)
    ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE APPLICATION.DASHBOARD_GROUP ADD FOREIGN KEY (USER_ID) REFERENCES APPLICATION.USER(ID)
    ON DELETE CASCADE ON UPDATE NO ACTION;


CREATE INDEX IDX_DASHBOARD_USER_USER ON APPLICATION.DASHBOARD_USER(USER_ID);
CREATE INDEX IDX_DASHBOARD_USER_DASHBOARD ON APPLICATION.DASHBOARD_USER(DASHBOARD_ID);
CREATE INDEX IDX_DASHBOARD_GROUP_USER ON APPLICATION.DASHBOARD_GROUP(USER_ID);

