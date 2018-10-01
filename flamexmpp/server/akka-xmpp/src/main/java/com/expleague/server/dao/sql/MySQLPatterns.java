package com.expleague.server.dao.sql;

import com.expleague.model.Pattern;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.dao.PatternsRepository;
import com.expleague.util.stream.RequiresClose;
import com.expleague.commons.io.StreamTools;

import java.io.IOException;
import java.sql.SQLException;
import java.util.stream.Stream;

/**
 * Experts League
 * Created by solar on 28/03/16.
 */
@SuppressWarnings("unused")
public class MySQLPatterns extends MySQLOps implements PatternsRepository {
  public MySQLPatterns() {
    super(ExpLeagueServer.config().db());
  }

  @RequiresClose
  @Override
  public Stream<Pattern> all() {
    try {
      return stream("SELECT * FROM Patterns", stmt -> {}).map(rs -> {
        try {
          final String name = rs.getString(1);
          final String body = StreamTools.readReader(rs.getCharacterStream(2)).toString();
          final String icon = rs.getString(3);
          Pattern.Type type = Pattern.Type.valueOf(rs.getInt(4));
          return new Pattern(name, body, icon, type);
        } catch (IOException | SQLException e) {
          throw new RuntimeException(e);
        }
      });
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}
