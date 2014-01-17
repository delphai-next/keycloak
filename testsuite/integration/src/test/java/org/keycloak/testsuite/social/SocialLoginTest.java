/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.keycloak.testsuite.social;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.keycloak.models.RealmModel;
import org.keycloak.representations.SkeletonKeyToken;
import org.keycloak.services.managers.RealmManager;
import org.keycloak.testsuite.DummySocialServlet;
import org.keycloak.testsuite.OAuthClient;
import org.keycloak.testsuite.OAuthClient.AccessTokenResponse;
import org.keycloak.testsuite.pages.AppPage;
import org.keycloak.testsuite.pages.AppPage.RequestType;
import org.keycloak.testsuite.pages.LoginPage;
import org.keycloak.testsuite.pages.LoginUpdateProfilePage;
import org.keycloak.testsuite.pages.RegisterPage;
import org.keycloak.testsuite.rule.KeycloakRule;
import org.keycloak.testsuite.rule.KeycloakRule.KeycloakSetup;
import org.keycloak.testsuite.rule.WebResource;
import org.keycloak.testsuite.rule.WebRule;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.util.HashMap;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class SocialLoginTest {

    @ClassRule
    public static KeycloakRule keycloakRule = new KeycloakRule(new KeycloakSetup() {
        @Override
        public void config(RealmManager manager, RealmModel defaultRealm, RealmModel appRealm) {
            appRealm.setSocial(true);
            appRealm.setUpdateProfileOnInitialSocialLogin(false);

            HashMap<String, String> socialConfig = new HashMap<String, String>();
            socialConfig.put("dummy.key", "1234");
            socialConfig.put("dummy.secret", "1234");
            appRealm.setSocialConfig(socialConfig);
        }
    });

    @Rule
    public WebRule webRule = new WebRule(this);

    @WebResource
    protected WebDriver driver;

    @WebResource
    protected AppPage appPage;

    @WebResource
    protected LoginPage loginPage;

    @WebResource
    protected LoginUpdateProfilePage profilePage;

    @WebResource
    protected OAuthClient oauth;

    @BeforeClass
    public static void before() {
        keycloakRule.deployServlet("dummy-social", "/dummy-social", DummySocialServlet.class);
    }

    @Test
    public void loginSuccess() throws Exception {
        loginPage.open();

        loginPage.clickSocial("dummy");

        driver.findElement(By.id("username")).sendKeys("dummy-user");
        driver.findElement(By.id("submit")).click();

        Assert.assertEquals(RequestType.AUTH_RESPONSE, appPage.getRequestType());

        AccessTokenResponse response = oauth.doAccessTokenRequest(oauth.getCurrentQuery().get("code"), "password");

        SkeletonKeyToken token = oauth.verifyToken(response.getAccessToken());

        Assert.assertEquals("dummy-user", token.getSubject());
    }

    @Test
    public void profileUpdateRequired() {
        keycloakRule.configure(new KeycloakSetup() {
            @Override
            public void config(RealmManager manager, RealmModel adminstrationRealm, RealmModel appRealm) {
                appRealm.setUpdateProfileOnInitialSocialLogin(true);
            }
        });

        try {
            loginPage.open();

            loginPage.clickSocial("dummy");

            driver.findElement(By.id("username")).sendKeys("dummy-user-reg");
            driver.findElement(By.id("submit")).click();

            profilePage.isCurrent();

            Assert.assertEquals("", profilePage.getFirstName());
            Assert.assertEquals("", profilePage.getLastName());
            Assert.assertEquals("dummy-user-reg@dummy-social", profilePage.getEmail());

            profilePage.update("Dummy", "User", "dummy-user-reg@dummy-social");

            Assert.assertEquals(RequestType.AUTH_RESPONSE, appPage.getRequestType());
        } finally {
            keycloakRule.configure(new KeycloakSetup() {
                @Override
                public void config(RealmManager manager, RealmModel adminstrationRealm, RealmModel appRealm) {
                    appRealm.setUpdateProfileOnInitialSocialLogin(false);
                }
            });
        }
    }

}
