/**
 * Copyright (c) 2010, Sebastian Sdorra
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 3. Neither the name of SCM-Manager; nor the names of its
 *    contributors may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * http://bitbucket.org/sdorra/scm-manager
 *
 */



package sonia.scm.auth.ldap;

//~--- non-JDK imports --------------------------------------------------------

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sonia.scm.user.User;
import sonia.scm.util.IOUtil;
import sonia.scm.util.Util;
import sonia.scm.web.security.AuthenticationResult;

//~--- JDK imports ------------------------------------------------------------

import java.text.MessageFormat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

/**
 *
 * @author Sebastian Sdorra
 */
public class LDAPAuthenticationContext
{

  /** Field description */
  public static final String ATTRIBUTE_GROUP_NAME = "cn";

  /** Field description */
  public static final String NESTEDGROUP_MATCHINGRULE =
    ":1.2.840.113556.1.4.1941:=";

  /** Field description */
  public static final String SEARCHTYPE_GROUP = "group";

  /** Field description */
  public static final String SEARCHTYPE_USER = "user";

  /** the logger for LDAPContext */
  private static final Logger logger =
    LoggerFactory.getLogger(LDAPAuthenticationContext.class);

  //~--- constructors ---------------------------------------------------------

  /**
   * Constructs ...
   *
   *
   * @param config
   */
  public LDAPAuthenticationContext(LDAPConfig config)
  {
    this.config = config;
    this.state = new LDAPAuthenticationState();
  }

  //~--- methods --------------------------------------------------------------

  /**
   * Method description
   *
   *
   * @param username
   * @param password
   *
   * @return
   */
  public AuthenticationResult authenticate(String username, String password)
  {
    AuthenticationResult result = AuthenticationResult.NOT_FOUND;
    LDAPConnection bindConnection = null;

    try
    {
      bindConnection = createBindConnection();

      if (bindConnection != null)
      {
        SearchResult searchResult = getUserSearchResult(bindConnection,
                                      username);

        if (searchResult != null)
        {
          result = AuthenticationResult.FAILED;

          String userDN = searchResult.getNameInNamespace();

          if (authenticateUser(userDN, password))
          {
            Attributes attributes = searchResult.getAttributes();
            User user = createUser(attributes);

            if (user.isValid())
            {
              state.setUserValid(true);

              Set<String> groups = new HashSet<String>();

              fetchGroups(bindConnection, groups, userDN, user.getId(),
                user.getMail());
              getGroups(attributes, groups);
              result = new AuthenticationResult(user, groups);
            }
            else if (logger.isWarnEnabled())
            {
              logger.warn("the returned user is not valid: {}", user);
            }
          }    // password wrong ?
        }      // user not found
      }        // no bind context available
    }
    finally
    {
      IOUtil.close(bindConnection);
    }

    return result;
  }

  //~--- get methods ----------------------------------------------------------

  /**
   * Method description
   *
   *
   * @return
   */
  public LDAPAuthenticationState getState()
  {
    return state;
  }

  //~--- methods --------------------------------------------------------------

  /**
   * Method description
   *
   *
   * @param list
   * @param attribute
   */
  private void appendAttribute(List<String> list, String attribute)
  {
    if (Util.isNotEmpty(attribute))
    {
      list.add(attribute);
    }
  }

  /**
   * Method description
   *
   *
   * @param userDN
   * @param password
   *
   * @return
   */
  private boolean authenticateUser(String userDN, String password)
  {
    boolean authenticated = false;
    LDAPConnection userConnection = null;

    try
    {
      userConnection = new LDAPConnection(config, userDN, password);
      authenticated = true;
      state.setAuthenticateUser(true);

      if (logger.isDebugEnabled())
      {
        logger.debug("user {} successfully authenticated", userDN);
      }
    }
    catch (Exception ex)
    {
      state.setAuthenticateUser(false);
      state.setException(ex);

      if (logger.isTraceEnabled())
      {
        logger.trace("authentication failed for user ".concat(userDN), ex);
      }
      else if (logger.isWarnEnabled())
      {
        logger.debug("authentication failed for user {}", userDN);
      }
    }
    finally
    {
      IOUtil.close(userConnection);
    }

    return authenticated;
  }

  /**
   * Method description
   *
   *
   * @return
   */
  private LDAPConnection createBindConnection()
  {
    LDAPConnection connection = null;

    try
    {
      connection = new LDAPConnection(config, config.getConnectionDn(),
        config.getConnectionPassword());
      state.setBind(true);
    }
    catch (Exception ex)
    {
      state.setBind(false);
      state.setException(ex);
      logger.error(
        "could not bind to ldap with dn ".concat(config.getConnectionDn()), ex);
      IOUtil.close(connection);
    }

    return connection;
  }

  /**
   * Method description
   *
   *
   * @return
   */
  private String createGroupSearchBaseDN()
  {
    return createSearchBaseDN(SEARCHTYPE_GROUP, config.getUnitGroup());
  }

  /**
   * Method description
   *
   *
   * @param userDN
   * @param uid
   * @param mail
   *
   * @return
   */
  private String createGroupSearchFilter(String userDN, String uid, String mail)
  {
    String filter = null;
    String filterPattern = config.getSearchFilterGroup();

    if (Util.isNotEmpty(filterPattern))
    {
      if (mail == null)
      {
        mail = "";
      }

      if (config.isEnableNestedADGroups())
      {
        filterPattern = prepareFilterPatternForNestedGroups(filterPattern,
          userDN);
      }

      filter = MessageFormat.format(filterPattern, userDN, uid, mail);

      if (logger.isDebugEnabled())
      {
        logger.debug("search-filter for group search: {}", filter);
      }
    }
    else if (logger.isWarnEnabled())
    {
      logger.warn("search-filter for groups not defined");
    }

    return filter;
  }

  /**
   * Method description
   *
   *
   * @param type
   * @param prefix
   *
   * @return
   */
  private String createSearchBaseDN(String type, String prefix)
  {
    String dn = null;

    if (Util.isNotEmpty(config.getBaseDn()))
    {
      if (Util.isNotEmpty(prefix))
      {
        dn = prefix.concat(",").concat(config.getBaseDn());
      }
      else
      {
        if (logger.isDebugEnabled())
        {
          logger.debug("no prefix for {} defined, using basedn for search",
            type);
        }

        dn = config.getBaseDn();
      }

      if (logger.isDebugEnabled())
      {
        logger.debug("saarch base for {} search: {}", type, dn);
      }
    }
    else if (logger.isErrorEnabled())
    {
      logger.error("no basedn defined");
    }

    return dn;
  }

  /**
   * Method description
   *
   *
   * @param attributes
   *
   * @return
   */
  private User createUser(Attributes attributes)
  {
    User user = new User();

    user.setName(LDAPUtil.getAttribute(attributes,
      config.getAttributeNameId()));
    user.setDisplayName(LDAPUtil.getAttribute(attributes,
      config.getAttributeNameFullname()));
    user.setMail(LDAPUtil.getAttribute(attributes,
      config.getAttributeNameMail()));
    user.setType(LDAPAuthenticationHandler.TYPE);

    return user;
  }

  /**
   * Method description
   *
   *
   * @return
   */
  private String createUserSearchBaseDN()
  {
    return createSearchBaseDN(SEARCHTYPE_USER, config.getUnitPeople());
  }

  /**
   * Method description
   *
   *
   * @param username
   *
   * @return
   */
  private String createUserSearchFilter(String username)
  {
    String filter = null;

    if (Util.isNotEmpty(config.getSearchFilter()))
    {
      filter = MessageFormat.format(config.getSearchFilter(), username);

      if (logger.isDebugEnabled())
      {
        logger.debug("search-filter for user search: {}", filter);
      }
    }
    else if (logger.isErrorEnabled())
    {
      logger.error("search filter not defined");
    }

    return filter;
  }

  /**
   * Method description
   *
   *
   * @param connection
   * @param groups
   * @param userDN
   * @param uid
   * @param mail
   */
  private void fetchGroups(LDAPConnection connection, Set<String> groups,
    String userDN, String uid, String mail)
  {
    if (Util.isNotEmpty(config.getSearchFilterGroup()))
    {
      logger.trace("try to fetch groups for user {}", uid);

      NamingEnumeration<SearchResult> searchResultEnm = null;

      try
      {

        // read group of unique names
        SearchControls searchControls = new SearchControls();

        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        // make group name attribute configurable?
        searchControls.setReturningAttributes(new String[] {
          ATTRIBUTE_GROUP_NAME });

        String filter = createGroupSearchFilter(userDN, uid, mail);

        if (filter != null)
        {
          String searchDN = createGroupSearchBaseDN();

          if (logger.isDebugEnabled())
          {
            logger.debug("search groups for user {} at {} with filter {}",
              new Object[] { userDN,
              searchDN, filter });
          }

          searchResultEnm = connection.search(searchDN, filter, searchControls);

          while (searchResultEnm.hasMore())
          {
            SearchResult searchResult = searchResultEnm.next();
            Attributes groupAttributes = searchResult.getAttributes();
            String name = LDAPUtil.getAttribute(groupAttributes,
                            ATTRIBUTE_GROUP_NAME);

            if (Util.isNotEmpty(name))
            {
              if (logger.isTraceEnabled())
              {
                logger.trace("append group {} with name {} to user result",
                  searchResult.getNameInNamespace(), name);
              }

              groups.add(name);
            }
            else if (logger.isDebugEnabled())
            {
              logger.debug("could not read group name from {}",
                searchResult.getNameInNamespace());
            }
          }
        }
      }
      catch (NamingException ex)
      {
        if (logger.isDebugEnabled())
        {
          logger.debug("could not find groups", ex);
        }
      }
      finally
      {
        LDAPUtil.close(searchResultEnm);
      }
    }
    else if (logger.isDebugEnabled())
    {
      logger.debug("group filter is empty");
    }
  }

  /**
   * Method description
   *
   *
   * @param filterPattern
   * @param userDN
   *
   * @return
   */
  private String prepareFilterPatternForNestedGroups(String filterPattern,
    String userDN)
  {
    return filterPattern.replaceAll(Pattern.quote("={0}"),
      NESTEDGROUP_MATCHINGRULE.concat(userDN));
  }

  //~--- get methods ----------------------------------------------------------

  /**
   * Method description
   *
   *
   * @param attributes
   * @param groups
   *
   */
  private void getGroups(Attributes attributes, Set<String> groups)
  {
    String groupAttribute = config.getAttributeNameGroup();

    if (Util.isNotEmpty(groupAttribute))
    {
      logger.trace("try to get groups from group attribute {}", groupAttribute);
      NamingEnumeration<?> userGroupsEnm = null;

      try
      {
        Attribute groupsAttribute = attributes.get(groupAttribute);

        if (groupsAttribute != null)
        {
          userGroupsEnm = groupsAttribute.getAll();

          while (userGroupsEnm.hasMore())
          {
            String group = (String) userGroupsEnm.next();

            group = LDAPUtil.getName(group);
            logger.debug("append group {} to user result", group);
            groups.add(group);
          }
        }
        else if (logger.isDebugEnabled())
        {
          logger.debug("user has no group attributes assigned");
        }
      }
      catch (NamingException ex)
      {
        logger.error("could not read group attribute", ex);
      }
      finally
      {
        LDAPUtil.close(userGroupsEnm);
      }
    }
    else if (logger.isDebugEnabled())
    {
      logger.debug("group attribute is empty");
    }
  }

  /**
   * Method description
   *
   *
   * @return
   */
  private String[] getReturnAttributes()
  {
    List<String> list = new ArrayList<String>();

    appendAttribute(list, config.getAttributeNameId());
    appendAttribute(list, config.getAttributeNameFullname());
    appendAttribute(list, config.getAttributeNameMail());
    appendAttribute(list, config.getAttributeNameGroup());

    return list.toArray(new String[list.size()]);
  }

  /**
   * Method description
   *
   *
   * @param bindConnection
   * @param username
   *
   * @return
   */
  private SearchResult getUserSearchResult(LDAPConnection bindConnection,
    String username)
  {
    SearchResult result = null;

    if (bindConnection != null)
    {
      NamingEnumeration<SearchResult> searchResultEnm = null;

      try
      {
        SearchControls searchControls = new SearchControls();
        int scope = LDAPUtil.getSearchScope(config.getSearchScope());

        if (logger.isDebugEnabled())
        {
          logger.debug("using scope {} for user search",
            LDAPUtil.getSearchScope(scope));
        }

        searchControls.setSearchScope(scope);
        searchControls.setCountLimit(1);
        searchControls.setReturningAttributes(getReturnAttributes());

        String filter = createUserSearchFilter(username);

        if (filter != null)
        {
          String baseDn = createUserSearchBaseDN();

          if (baseDn != null)
          {
            searchResultEnm = bindConnection.search(baseDn, filter,
              searchControls);

            if (searchResultEnm.hasMore())
            {
              result = searchResultEnm.next();
              state.setSearchUser(true);
            }
            else if (logger.isWarnEnabled())
            {
              logger.warn("no user with username {} found", username);
            }
          }
        }
      }
      catch (NamingException ex)
      {
        state.setSearchUser(false);
        state.setException(ex);

        if (logger.isErrorEnabled())
        {
          logger.error("exception occured during user search", ex);
        }
      }
      finally
      {
        LDAPUtil.close(searchResultEnm);
      }
    }

    return result;
  }

  //~--- fields ---------------------------------------------------------------

  /** Field description */
  private LDAPConfig config;

  /** Field description */
  private LDAPAuthenticationState state;
}
