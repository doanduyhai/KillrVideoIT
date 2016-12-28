package com.datastax.killrvideo.it.dao;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import com.datastax.driver.core.*;

import info.archinnov.achilles.script.ScriptExecutor;

@Repository
public class CassandraDao {

    private static Logger LOGGER = LoggerFactory.getLogger(CassandraDao.class);


    public final Session session;
    public final PreparedStatement findUserByEmailPs;
    public final PreparedStatement findVideoByIdPs;

    @Inject
    public CassandraDao(Session session) {
        this.session = session;
        maybeCreateSchema(session);
        this.findUserByEmailPs = session.prepare("SELECT * FROM killrvideo.user_credentials WHERE email = ?");
        this.findVideoByIdPs = session.prepare("SELECT added_date FROM killrvideo.videos WHERE videoid = ?");
    }

    public Row getOne(BoundStatement bs) {
        return this.session.execute(bs).one();
    }

    public void truncate(String tablename) {
        session.execute("TRUNCATE killrvideo." + tablename);
    }

    private void maybeCreateSchema(Session session) {
        LOGGER.info("Execute schema creation script 'schema.cql' if necessary");
        final ScriptExecutor scriptExecutor = new ScriptExecutor(session);
        scriptExecutor.executeScript("schema.cql");
    }
}
