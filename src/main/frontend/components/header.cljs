(ns frontend.components.header
  (:require [frontend.components.export :as export]
            [frontend.components.plugins :as plugins]
            [frontend.components.repo :as repo]
            [frontend.components.right-sidebar :as sidebar]
            [frontend.components.search :as search]
            [frontend.components.svg :as svg]
            [frontend.config :as config]
            [frontend.context.i18n :as i18n]
            [frontend.handler :as handler]
            [frontend.handler.page :as page-handler]
            [frontend.handler.plugin :as plugin-handler]
            [frontend.handler.user :as user-handler]
            [frontend.handler.web.nfs :as nfs]
            [frontend.modules.shortcut.core :as shortcut]
            [frontend.state :as state]
            [frontend.ui :as ui]
            [frontend.util :as util]
            [cljs-bean.core :as bean]
            [reitit.frontend.easy :as rfe]
            [rum.core :as rum]
            [frontend.mobile.util :as mobile-util]))

(rum/defc logo < rum/reactive
  [{:keys [white? electron-mac?]}]
  [:a.cp__header-logo
   {:class (when electron-mac? "button")
    :href     (rfe/href :home)
    :on-click (fn []
                (util/scroll-to-top)
                (state/set-journals-length! 2))}
   (if electron-mac?
     svg/home
     (if-let [logo (and config/publishing?
                        (get-in (state/get-config) [:project :logo]))]
       [:img.cp__header-logo-img {:src logo}]
       (svg/logo (not white?))))])

(rum/defc login
  [logged?]
  (rum/with-context [[t] i18n/*tongue-context*]
    (when (and (not logged?)
               (not config/publishing?))

      (ui/dropdown-with-links
       (fn [{:keys [toggle-fn]}]
         [:a.button.text-sm.font-medium.block {:on-click toggle-fn
                                               :style {:margin-right 12}}
          [:span (t :login)]])
       (let [list [;; {:title (t :login-google)
                   ;;  :url (str config/website "/login/google")}
                   {:title (t :login-github)
                    :url (str config/website "/login/github")}]]
         (mapv
          (fn [{:keys [title url]}]
            {:title title
             :options
             {:on-click
              (fn [_] (set! (.-href js/window.location) url))}})
          list))
       nil))))

(rum/defc left-menu-button < rum/reactive
  [{:keys [on-click]}]
  (let [left-sidebar-open? (state/sub :ui/left-sidebar-open?)]

    (ui/tippy
      {:html [:div.text-sm.font-medium
              "Shortcut: "
              [:code (util/->platform-shortcut "t l")]]
       :delay 500
       :hideDelay 1
       :position "right"
       :interactive true
       :arrow true}

      [:button#left-menu.cp__header-left-menu
       {:on-click on-click}
       (ui/icon
         (if left-sidebar-open?
           "indent-increase" "indent-decrease")
         {:style {:fontSize "22px"}})])
    ))

(rum/defc dropdown-menu < rum/reactive
  [{:keys [me current-repo t default-home]}]
  (let [projects (state/sub [:me :projects])
        developer-mode? (state/sub [:ui/developer-mode?])
        logged? (state/logged?)]
    (ui/dropdown-with-links
     (fn [{:keys [toggle-fn]}]
       [:a.cp__right-menu-button.button
        {:on-click toggle-fn}
        (svg/horizontal-dots nil)])
     (->>
      [(when current-repo
         {:title (t :cards-view)
          :options {:on-click #(state/pub-event! [:modal/show-cards])}})

       (when current-repo
         {:title (t :graph-view)
          :options {:href (rfe/href :graph)}
          :icon svg/graph-sm})

       (when current-repo
         {:title (t :all-pages)
          :options {:href (rfe/href :all-pages)}
          :icon svg/pages-sm})

       (when (and current-repo (not config/publishing?))
         {:title (t :all-files)
          :options {:href (rfe/href :all-files)}
          :icon svg/folder-sm})

       (when (and default-home current-repo)
         {:title (t :all-journals)
          :options {:href (rfe/href :all-journals)}
          :icon svg/calendar-sm})

       {:hr true}

       (when-not (state/publishing-enable-editing?)
         {:title (t :settings)
          :options {:on-click state/open-settings!}
          :icon svg/settings-sm})

       (when (and developer-mode? (util/electron?))
         {:title (t :plugins)
          :options {:href (rfe/href :plugins)}})

       (when developer-mode?
         {:title (t :themes)
          :options {:on-click #(plugins/open-select-theme!)}})

       (when current-repo
         {:title (t :export)
          :options {:on-click #(state/set-modal! export/export)}
          :icon nil})

       (when current-repo
         {:title (t :import)
          :options {:href (rfe/href :import)}
          :icon svg/import-sm})

       {:title [:div.flex-row.flex.justify-between.items-center
                [:span (t :join-community)]]
        :options {:href "https://discord.gg/KpN4eHY"
                  :title (t :discord-title)
                  :target "_blank"}
        :icon svg/discord}
       (when logged?
         {:title (t :sign-out)
          :options {:on-click user-handler/sign-out!}
          :icon svg/logout-sm})]
      (remove nil?))
     ;; {:links-footer (when (and (util/electron?) (not logged?))
     ;;                  [:div.px-2.py-2 (login logged?)])}
     )))

(rum/defc back-and-forward
  [electron-mac?]
  [:div.flex.flex-row
   [:a.it.navigation.nav-left.button
    {:title "Go Back" :on-click #(js/window.history.back)}
    svg/arrow-narrow-left]
   [:a.it.navigation.nav-right.button
    {:title "Go Forward" :on-click #(js/window.history.forward)}
    svg/arrow-narrow-right]])

(rum/defc updater-tips-new-version
  [t]
  (let [[downloaded, set-downloaded] (rum/use-state nil)
        _ (rum/use-effect!
            (fn []
              (when-let [channel (and (util/electron?) "auto-updater-downloaded")]
                (let [callback (fn [_ & args]
                                 (js/console.debug "[new-version downloaded] args:" args)
                                 (let [args (bean/->clj args)]
                                   (set-downloaded args)
                                   (state/set-state! :electron/auto-updater-downloaded args))
                                 nil)]
                  (js/apis.addListener channel callback)
                  #(js/apis.removeListener channel callback))))
            [])]

    (when downloaded
      [:div.cp__header-tips
       [:p (t :updater/new-version-install)
        [:a.ui__button.restart
         {:on-click #(handler/quit-and-install-new-version!)}
         (svg/reload 16) [:strong (t :updater/quit-and-install)]]]])))

(rum/defc header < rum/reactive
  [{:keys [open-fn current-repo white? logged? page? route-match me default-home new-block-mode]}]
  (let [local-repo? (= current-repo config/local-repo)
        repos (->> (state/sub [:me :repos])
                   (remove #(= (:url %) config/local-repo)))
        electron-mac? (and util/mac? (util/electron?))
        electron-not-mac? (and (util/electron?) (not electron-mac?))
        show-open-folder? (and (or (nfs/supported?)
                                   (mobile-util/is-native-platform?))
                               (empty? repos)
                               (not config/publishing?))
        refreshing? (state/sub :nfs/refreshing?)]
    (rum/with-context [[t] i18n/*tongue-context*]
      [:div.cp__header#head
       {:class (when electron-mac? "electron-mac")
        :on-double-click (fn [^js e]
                           (when-let [target (.-target e)]
                             (when (and (util/electron?)
                                        (or (.. target -classList (contains "cp__header"))))
                               (js/window.apis.toggleMaxOrMinActiveWindow))))}
       (left-menu-button {:on-click (fn []
                                      (open-fn)
                                      (state/set-left-sidebar-open!
                                        (not (:ui/left-sidebar-open? @state/state))))})

       (when-not electron-mac?
         (logo {:white? white?}))

       (when electron-not-mac? (back-and-forward))

       [:div.flex-1.flex]

       (when current-repo
         (ui/tippy
          {:html [:div.text-sm.font-medium
                  "Shortcut: "
                  [:code (util/->platform-shortcut "Ctrl + k")]]
           :interactive     true
           :arrow true}
          [:a.button#search-button
           {:on-click #(state/pub-event! [:go/search])}
           svg/search]))

       (when plugin-handler/lsp-enabled?
         (plugins/hook-ui-items :toolbar))

       [:a (when refreshing?
             [:div {:class "animate-spin-reverse"}
              svg/refresh])]

       (when electron-mac?
         (logo {:white? white?
                :electron-mac? true}))
       (when electron-mac? (back-and-forward true))

       (new-block-mode)

       (when refreshing?
         [:div {:class "animate-spin-reverse"}
          svg/refresh])

       (when (and
              (not (mobile-util/is-native-platform?))
              (not (util/electron?)))
         (login logged?))

       (repo/sync-status current-repo)

       (when-not (util/mobile?)
         [:div.repos
          (repo/repos-dropdown nil nil)])

       (when show-open-folder?
         [:a.text-sm.font-medium.button
          {:on-click #(page-handler/ls-dir-files! shortcut/refresh!)}
          [:div.flex.flex-row.text-center.open-button__inner.items-center
           [:span.inline-block.open-button__icon-wrapper svg/folder-add]
           (when-not config/mobile?
             [:span.ml-1 {:style {:margin-top (if electron-mac? 0 2)}}
              (t :open)])]])

       (when config/publishing?
         [:a.text-sm.font-medium.button {:href (rfe/href :graph)}
          (t :graph)])

       (dropdown-menu {:me me
                       :t t
                       :current-repo current-repo
                       :default-home default-home})

       (when (not (state/sub :ui/sidebar-open?))
         (sidebar/toggle))

       (updater-tips-new-version t)])))
