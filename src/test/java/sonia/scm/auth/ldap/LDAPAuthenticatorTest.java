package sonia.scm.auth.ldap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sonia.scm.user.User;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LDAPAuthenticatorTest extends LDAPServerTestBaseJunit5 {

  private LDAPConfig config;
  private LDAPAuthenticator authenticator;

  @BeforeEach
  void setUpAuthenticator() {
    config = createConfig();
    authenticator = new LDAPAuthenticator(config);
  }

  @Test
  void shouldAuthenticateUserTrillian() {
    ldif(1);

    Optional<User> optionalUser = authenticator.authenticate("trillian", "trilli123");
    assertThat(optionalUser).isPresent();

    User trillian = optionalUser.get();
    assertTrillian(trillian);
  }

  @Test
  void shouldReturnEmptyOptional() {
    ldif(1);

    Optional<User> optionalUser = authenticator.authenticate("unknown", "secret");
    assertThat(optionalUser).isEmpty();
  }

  @Test
  void shouldThrowBindConnectionFailedException() {
    ldif(1);
    config.setConnectionPassword("totally wrong");
    assertThrows(BindConnectionFailedException.class, () -> authenticator.authenticate("trillian", "trilli123"));
  }

  @Test
  void shouldThrowUserSearchFailedException() {
    ldif(1);
    config.setUnitPeople("cn=totally wrong");
    assertThrows(UserSearchFailedException.class, () -> authenticator.authenticate("trillian", "trilli123"));
  }

  @Test
  void shouldThrowUserAuthenticationFailedException() {
    ldif(1);
    assertThrows(UserAuthenticationFailedException.class, () -> authenticator.authenticate("trillian", "i_don't_know"));
  }

  @Test
  void shouldThrowInvalidUserException() {
    ldif(5);
    assertThrows(InvalidUserException.class, () -> authenticator.authenticate("trillian", "trilli123"));
  }

  @Test
  void shouldThrowConfigurationExceptionIfNoBaseDNWasDefined() {
    config.setBaseDn(null);
    assertThrows(ConfigurationException.class, () -> authenticator.authenticate("trillian", "trilli123"));
  }

  @Test
  void shouldThrowConfigurationExceptionIfUserSearchFilterWasNotDefined() {
    config.setSearchFilter(null);
    assertThrows(ConfigurationException.class, () -> authenticator.authenticate("trillian", "trilli123"));
  }

  private void assertTrillian(User user) {
    assertThat(user.getType()).isEqualTo("ldap");
    assertThat(user.getName()).isEqualTo("trillian");
    assertThat(user.getDisplayName()).isEqualTo("Tricia McMillan");
    assertThat(user.getMail()).isEqualTo("tricia.mcmillan@hitchhiker.com");
  }

}
