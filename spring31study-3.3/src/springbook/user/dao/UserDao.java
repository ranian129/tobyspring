package springbook.user.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.dao.EmptyResultDataAccessException;

import springbook.user.domain.User;

/**
 * JDBC를 이용한 등록과 조회 기능이 있는 USerDao 클래스.
 * 
 * 1.2 DAO의 분리 (관심사의 분리)
 *  - UserDao의 관심사항
 * 	  1. DB와 연결을 위한 커넥션을 어떻게 가져올까.
 * 	  2. 사용자 등록을 위해 DB에 보낼 SQL 문장을 담을 Statement를 만들고 실행하는 것.
 * 	  3. 작업이 끝나면 사용한 리소스인 Statement와 Connection 리소스를 돌려주는 것.
 * 
 *  - 원칙과 패턴
 *    - 개방 폐쇄 원칙 (OCP)
 *    - 높은 응집도와 낮은 결합도.
 *    - 전략 패턴 Strategy pattern
 *      - UserDaoTest -> Userdao -> ConnectionMaker <- DConnectionMaker
 *      
 *  - 의존관계 주입 조건
 *    1. 인터페이스의 파라미터를 통해 주입빋음.
 *    2. 제 3의 존재가 의존관계를 결정한다. (컨테이너, 팩토리)
 *    3. 레퍼런스를 외부에서 제공(주입)해 줌으로써 만들어짐.
 *      -> 설계 시점에는 알지 못했던 두 오브젝트의 관계를 맺도록 도와주는 제 3의 존재가 있다는 게 핵심.
 *  
 *  2. 테스트
 *  2.3 개발자를 위한 테스팅 프레임워크 JUnit.
 *    - deleteAll(), getcount() 추가.
 *    
 *  3. 템플릿
 *    - 템플릿: 일정한 패턴으로 유지되는 특성을 가진 부분을 독립시켜서 활용하는 방법.
 *    - 3장 목적
 *    	1. 스프링에 적용된 템플릿 기법을 살펴보고,
 *    	2. 이를 적용해 완성도 있는 DAO 코드를 만드는 법을 알아본다.
 *  3.1 다시보는 초난감 DAO
 *    - UserDao의 문제점 --> 예외처리가 빠져 있음.
 *    - 예외처리 기능을 갖춘 DAO
 *    	- JDBC 수정 기능의 예외처리
 *    	- JDBC 조회 기능의 예외처리
 *  3.2 변하는 것과 변하지 않는 것
 *    - JDBC try/catch/finnaly 코드의 문제점
 *      - 복잡한 try/catch/finnaly 코드의 중복
 *      - 수정 중에 c.close() 같은 코드가 빠지면 큰 문제가 발생할 수도 있음.
 *      - 문제의 핵심: 변하지 않는, 그러나 많은 곳에서 중복되는 코드와 로직을 분리하지 않은 것.
 *    - 분리와 재사용을 위한 디자인 패턴 적용
 *    	- 성격이 다른 것을 찾아내자!
 *    	- 템플릿 메소드 패턴의 적용
 *    	  1. 상속을 통해 기능을 확장해서 사용한다.
 *    	  2. 변하지 않는 부분은 슈퍼클래스에 둔다.
 *    	  3. 변하는 부분은 추상 메소드로 정의해둬서 서브클래스에서 오버라이드해서 새롭게 정의한다.
 *    	  - 문제점
 *    		-> DAO 로직마다 상속을 통해 새로운 클래스를 만들어야 한다.
 *    		-> 확장구조가 이미 클래스를 설계하는 시점에서 고정되어 버린다.
 *    	- 전략 패턴의 적용
 *    	  1. 오브젝트를 아예 둘로 분리한다.
 *    	  2. 클래스 레벨에서는 인터페이스를 통해서만 의존하도록 만든다.
 *    	  - 특정 확장 기능은 전략 인터페이스를 통해 외부의 독립된 전략 클래스에 위임한다.
 *    		-> PreparedStatement를 만드는 부분을 외부 기능을 호출하여 완성시킴.
 *    		-> 인터페이스의 메소드를 통해 PreparedStatement 생성 전략을 변경하도록 함.
 *    	  - 문제점 -> 아직 구현 클래스가 UserDao에 고정되어 있다!!!
 *    	- DI 적용을 위한 클라이언트/컨텍스트 분리
 *    	  - 전략 패턴의 실제적인 사용 방법
 *    		- Context가 어떤 전략을 사용하게 할 것인가는 Client가 결정하는 게 일반적이다!!
 *    		- Client가 구체적인 전략의 하나를 선택하고 오브젝트로 만들어서 Context에 전달한다.
 *    		- Context는 전달받은 그 Strategy 구현 클래스의 오브젝트를 사용한다.
 *    	  - DI : 이런 전략 패턴의 장점을 일반적으로 활용할 수 있도록 만든 구조.
 *
 *  3.3 JDBC 전략 패턴의 최적화
 *    - 전략 클래스의 추가 정보
 *    	- add() 메소드에도 적용.
 *    	  --> 부가정보인 user를 제공해 줄 수 없는 상황이 발생함!
 *    		--> User 정보를 생성자로부터 제공 받도록 만듬.
 *    - 전략과 클라이언트의 동거
 *      - 현재 만들어진 구조에 두 가지 불만.
 *        1. DAO 메소드마다 새로운 StatementStrategy 구현 클래스를 만들어야 한다.
 *        2. User와 같은 부가정보가 있는 경우, 생성자와 인스턴스 변수를 번거롭게 만들어야 한다.
 *      - 어떻게 해결할 수 있을까?
 *        1. 로컬 클래스를 사용
 *        	- 둘 다 UserDao에서만 사용되고, UserDao 메소드 로직에 강하게 결합되어 있음.
 *          -> 로컬 클래스로 만들어도 좋은 상태임.
 *          --> 클래스 파일이 하나 줄어듬.
 *          --> User 같은 부가정보가 있어서 쉽게 접근 가능.
 *        2. 익명 내부 클래스를 사용
 *          - 좀 더 간결하게 클래스 이름도 제거 가능!
 * @author kjlee
 *
 */
public class UserDao {
	
	private DataSource dataSource;
	
	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public UserDao() {}
	
	public void add(final User user) throws SQLException {
		jdbcContextWithStatementStrategy(
			new StatementStrategy() {
				@Override
				public PreparedStatement makeStatement(Connection c) throws SQLException {
					PreparedStatement ps = c.prepareStatement("insert into users(id, name, password) values(?,?,?)");
					ps.setString(1, user.getId());
					ps.setString(2, user.getName());
					ps.setString(3, user.getPassword());
					
					return ps;
				}
			}
		);
	}
	
	public User get(String id) throws SQLException {
		Connection c = dataSource.getConnection();
		
		PreparedStatement ps = c.prepareStatement("select * from users where id = ?");
		ps.setString(1, id);
		
		User user = null;
		
		ResultSet rs = ps.executeQuery();
		if (rs.next()) {
			user = new User();
			user.setId(rs.getString("id"));
			user.setName(rs.getString("name"));
			user.setPassword(rs.getString("password"));
		}
		
		rs.close();
		ps.close();
		c.close();
		
		// 결과가 없으면 지정한 예외를 던져주도록 한다.
		if (user == null)
			throw new EmptyResultDataAccessException(1);
		
		return user;
	}
	
	public void deleteAll() throws SQLException {
		jdbcContextWithStatementStrategy(
			new StatementStrategy() {
				@Override
				public PreparedStatement makeStatement(Connection c) throws SQLException {
					PreparedStatement ps = c.prepareStatement("delete from users");
					return ps;
				}
			}
		);
	}
	
	public void jdbcContextWithStatementStrategy(StatementStrategy stmt) throws SQLException {
		Connection c = null;
		PreparedStatement ps = null;

		try {
			c = dataSource.getConnection();
			ps = stmt.makeStatement(c);
			
			ps.executeUpdate();
		} catch (SQLException e) {
			throw e;
		} finally {
			if (ps != null) {
				try { ps.close(); } catch (SQLException e) {}
			}
			if (c != null) {
				try { c.close(); } catch (SQLException e) {}
			}
		}
	}
	
	public int getCount() throws SQLException {
		
		Connection c = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		
		try {
			c = dataSource.getConnection();
			ps = c.prepareStatement("select count(*) from users");
			
			rs = ps.executeQuery();
			rs.next();
			
			return rs.getInt(1);
		} catch (SQLException e) {
			throw e;
		} finally {
			if (rs != null) {
				try { rs.close(); } catch (SQLException e) {}
			}
			if (ps != null) {
				try { ps.close(); } catch (SQLException e) {}
			}
			if (c != null) {
				try { c.close(); } catch (SQLException e) {}
			}
		}
	}
	
}
