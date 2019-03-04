/*
 * @Author: tiero
 * @Date: 2018-01-05 13:20:00
 * @Last Modified by: tiero
 * @Last Modified time: 2017-01-05 17:40:19
 */
import React, { Component } from 'react'
import PropTypes from 'prop-types'
import { requireNativeComponent, Image, Platform } from 'react-native'

class PanoramaView extends Component {
  render () {
    return <RCTPanoramaView {...this.props} />
  }
}

const componentName = Platform.OS === 'ios' ? 'Panorama' : 'RNGoogleVRPanorama';
// requireNativeComponent automatically resolves this to "PanoramaManager"
var RCTPanoramaView = requireNativeComponent(componentName, PanoramaView)
export default PanoramaView
