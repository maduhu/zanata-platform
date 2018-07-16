// @ts-nocheck
import React from 'react'
import { storiesOf } from '@storybook/react'
import { Link } from '../'

storiesOf('Link', module)
    .add('link within frontend app', () => (
        <span>
      <h2><img
          src="https://upload.wikimedia.org/wikipedia/commons/4/49/Zanata-Logo.svg"
          width="42px"/> Link in frontend</h2>
          <p> Common link component which generates <code>a href</code> or in-page navigation link based on useHref.</p>
      <Link link='/languages'>Languages</Link>
          <hr />
          <h3>Props</h3>
          <table>
          <thead>
          <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
          </thead>
          <tbody>
           <tr>
            <td>id</td>
            <td>string</td>
            <td></td>
            <td>ID attribute</td>
          </tr>
          <tr>
            <td>link</td>
            <td>string</td>
            <td></td>
            <td>HTML url or location#hash</td>
          </tr>
          <tr>
            <td>useHref</td>
            <td>bool</td>
            <td>false</td>
            <td>Toggle whether to use <code>a href</code> or in-page navigation</td>
          </tr>
           <tr>
            <td>children</td>
            <td>node</td>
            <td></td>
            <td></td>
          </tr>
          </tbody>
          </table>
        </span>
    ))
    .add('link page not in frontend app', () => (
        <span>
      <h2><img
          src="https://upload.wikimedia.org/wikipedia/commons/4/49/Zanata-Logo.svg"
          width="42px"/> Link *not* in frontend</h2>
          <p>Use this link colour anywhere outside of frontend. ie. Editor, zanata.org</p>
      <Link link='http://zanata.org/language/view/ja' useHref>
        Japanese
      </Link>
        </span>
    ))
